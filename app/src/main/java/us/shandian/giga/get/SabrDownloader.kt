package us.shandian.giga.get

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.youtube.SabrAttestationRetryHandler
import org.schabi.newpipe.youtube.SabrRequestCoordinator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.function.BooleanSupplier

internal class SabrDownloader(
    private val mission: DownloadMission,
) : Runnable {
    override fun run() {
        try {
            ensureRunning()
            val recoveries = validateRecoveryInfo()
            val info = SabrDownloadFormatResolver.resolveInfo(recoveries)

            val expectedLength = recoveries.map { recovery ->
                when (recovery.kind) {
                    'a' -> SabrDownloadFormatResolver.selectedAudioFormat(info, arrayOf(recovery))
                    'v' -> SabrDownloadFormatResolver.selectedVideoFormat(info, arrayOf(recovery))
                    else -> null
                }
            }.takeIf { formats -> formats.all { it != null && it.contentLength > 0 } }
                ?.sumOf { it!!.contentLength }
                ?: 0L
            prepareMission(expectedLength)
            val attestationRetryHandler = SabrAttestationRetryHandler(info.videoId)
            runSessionAttempt(info, recoveries, attestationRetryHandler)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: InterruptedIOException) {
            Thread.currentThread().interrupt()
        } catch (error: SabrProtocolException) {
            notifyErrorAndCleanup(classifyProtocolException(error))
        } catch (error: Exception) {
            notifyErrorAndCleanup(error)
        }
    }

    @Throws(IOException::class, InterruptedException::class, SabrProtocolException::class)
    private fun runSessionAttempt(
        info: YoutubeSabrInfo,
        recoveries: Array<MissionRecoveryInfo>,
        attestationRetryHandler: SabrAttestationRetryHandler,
    ) {
        val session = YoutubeSabrSession(info)
        val poToken = info.poToken
            ?: throw SabrProtocolException("SABR info has no player PO token")
        session.setPoToken(poToken)
        val workDir = prepareWorkDirectory()
        val targets = SabrDownloadFormatResolver.buildTargets(info, recoveries, workDir)
        restoreTargets(targets)
        val outputs = mutableMapOf<Int, FileOutputStream>()
        try {
            targets.forEach { target ->
                outputs[target.resourceIndex] = FileOutputStream(target.file, true)
            }
        } catch (error: IOException) {
            outputs.values.forEach { output ->
                try {
                    output.close()
                } catch (ignored: IOException) {
                }
            }
            throw storageException("could not open temporary media", error)
        }

        try {
            val requestCoordinator = SabrRequestCoordinator(
                session,
                attestationRetryHandler,
                null,
            )
            downloadSegments(
                requestCoordinator,
                targets,
                SabrSegmentWriter(targets, outputs, ::reportBytesWritten),
            )
        } finally {
            outputs.values.forEach { output ->
                try {
                    output.close()
                } catch (ignored: Exception) {
                    // Nothing to do.
                }
            }
        }

        ensureRunning()
        val finalBytes = SabrFfmpegMuxer(mission).remuxAndCopy(
            targets.map { it.file },
            targets,
            workDir,
        )
        completeMission(finalBytes)
    }

    @Throws(IOException::class)
    private fun validateRecoveryInfo(): Array<MissionRecoveryInfo> {
        val recoveries = mission.recoveryInfo ?: throw IOException("Missing SABR recovery info")
        if (recoveries.size != mission.urls.size || recoveries.any { !it.isSabr }) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.FORMAT,
                "SABR download failed: mixed SABR/non-SABR resources are not supported",
            )
        }
        return recoveries
    }

    private fun prepareMission(expectedLength: Long) {
        if (mission.sabrCheckpoint?.version != SabrDownloadCheckpoint.VERSION) {
            mission.sabrCheckpoint = null
            cleanup(mission)
        }
        mission.done = restoredProgress()
        mission.nearLength = expectedLength.coerceAtLeast(mission.nearLength)
        mission.unknownLength = mission.nearLength <= 0
        mission.sabrStarted = true
        if (mission.nearLength > 0) {
            mission.length = mission.length
                .coerceAtLeast(mission.nearLength)
                .coerceAtLeast(mission.done)
        }
        mission.current = 0
        mission.writeThisToFile()
    }

    private fun reportBytesWritten(target: SabrDownloadTarget, delta: Long) {
        if (delta <= 0) {
            return
        }
        updateCheckpoint(target)
        if (mission.nearLength > 0) {
            mission.length = mission.length
                .coerceAtLeast(mission.nearLength)
                .coerceAtLeast(mission.done + delta)
            mission.unknownLength = false
        }
        mission.notifyProgress(delta)
    }

    @Throws(IOException::class)
    private fun prepareWorkDirectory(): File {
        val workDir = workDirectory(mission)
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw storageException("could not create temporary directory", null)
        }
        return workDir
    }

    private fun restoreTargets(targets: List<SabrDownloadTarget>) {
        try {
            targets.forEach { target ->
                val checkpoint = mission.sabrCheckpoint?.resources?.firstOrNull {
                    it.resourceIndex == target.resourceIndex &&
                        it.itag == target.format.itag &&
                        it.xtags == target.format.xtags &&
                        it.tempFilePath == target.file.absolutePath &&
                        it.nextWriteSequence > 0 &&
                        it.bytesWritten >= it.initializationBytes &&
                        it.initializationBytes >= 0 &&
                        it.initializationBytes <= MAX_INITIALIZATION_BYTES &&
                        target.file.exists() &&
                        target.file.length() >= it.bytesWritten
                }
                if (checkpoint == null) {
                    if (target.file.exists() && !target.file.delete()) {
                        throw IOException("Could not reset ${target.file}")
                    }
                    return@forEach
                }
                RandomAccessFile(target.file, "rw").use { file ->
                    file.setLength(checkpoint.bytesWritten)
                    if (checkpoint.initializationBytes > 0) {
                        val initialization = ByteArray(checkpoint.initializationBytes)
                        file.seek(0)
                        file.readFully(initialization)
                        target.initializationData = initialization
                        target.initializationWritten = true
                    }
                }
                target.nextWriteSequence = checkpoint.nextWriteSequence
            }
        } catch (error: IOException) {
            throw storageException("could not restore temporary media", error)
        }
    }

    private fun storageException(message: String, cause: IOException?): SabrDownloadException {
        return SabrDownloadException(
            SabrDownloadException.Reason.STORAGE,
            "SABR download failed: $message",
            cause,
        )
    }

    private fun updateCheckpoint(target: SabrDownloadTarget) {
        val current = mission.sabrCheckpoint ?: SabrDownloadCheckpoint()
        val resources = current.resources
            .filterNot { it.resourceIndex == target.resourceIndex }
            .toMutableList()
        val previousInitializationBytes = current.resources
            .firstOrNull { it.resourceIndex == target.resourceIndex }
            ?.initializationBytes
            ?: 0
        resources += SabrResourceCheckpoint(
            resourceIndex = target.resourceIndex,
            itag = target.format.itag,
            xtags = target.format.xtags,
            tempFilePath = target.file.absolutePath,
            nextWriteSequence = target.nextWriteSequence,
            bytesWritten = target.file.length(),
            initializationBytes = if (target.initializationWritten && previousInitializationBytes == 0) {
                target.initializationData?.size ?: 0
            } else {
                previousInitializationBytes
            },
        )
        mission.sabrCheckpoint = current.copy(resources = resources.sortedBy { it.resourceIndex })
    }

    private fun restoredProgress(): Long {
        return mission.sabrCheckpoint?.resources
            ?.filter { checkpoint ->
                File(checkpoint.tempFilePath).let { it.exists() && it.length() >= checkpoint.bytesWritten }
            }
            ?.sumOf { it.bytesWritten }
            ?: 0L
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadSegments(
        requestCoordinator: SabrRequestCoordinator,
        targets: List<SabrDownloadTarget>,
        writer: SabrSegmentWriter,
    ) {
        writer.observeWrittenInitializations()
        prepareInitializations(requestCoordinator, targets, writer)
        writer.observeWrittenInitializations()

        var noProgressResponses = 0
        while (true) {
            ensureRunning()
            writer.observeWrittenInitializations()
            if (isDownloadComplete(targets)) {
                break
            }

            val playerTimeMs = downloadPlayerTimeMs(targets)
            val audio = targets.firstOrNull { it.format.isAudio }
            val video = targets.firstOrNull { it.format.isVideo }
            val tracks = listOfNotNull(
                audio?.let {
                    YoutubeSabrRequest.Track.of(
                        it.format,
                        it.timeline,
                        it.nextWriteSequence - 1,
                    )
                },
                video?.let {
                    YoutubeSabrRequest.Track.of(
                        it.format,
                        it.timeline,
                        it.nextWriteSequence - 1,
                    )
                },
            )
            val sequencesBeforeRequest = targets.map { it.nextWriteSequence }
            requestWithNetworkRetry(
                requestCoordinator,
                YoutubeSabrRequest.playback(playerTimeMs, 1.0f, tracks),
                writer,
            )
            writer.observeWrittenInitializations()
            if (isDownloadComplete(targets)) {
                break
            }
            val madeProgress = targets.indices.any { index ->
                targets[index].nextWriteSequence > sequencesBeforeRequest[index]
            }
            if (madeProgress) {
                noProgressResponses = 0
            } else {
                noProgressResponses++
                if (noProgressResponses >= MAX_NO_PROGRESS_RESPONSES) {
                    throw SabrDownloadException(
                        SabrDownloadException.Reason.STALLED,
                        "SABR download stalled: no target sequence advanced after " +
                            "$MAX_NO_PROGRESS_RESPONSES responses",
                    )
                }
                Thread.sleep(IDLE_POLL_MS)
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun prepareInitializations(
        requestCoordinator: SabrRequestCoordinator,
        targets: List<SabrDownloadTarget>,
        writer: SabrSegmentWriter,
    ) {
        val pendingTargets = targets.filterNot { it.initializationWritten }
        if (pendingTargets.isEmpty()) {
            return
        }

        ensureRunning()
        requestWithNetworkRetry(
            requestCoordinator,
            YoutubeSabrRequest.preparation(0L, targets.map { it.format }),
            writer,
            { pendingTargets.all { it.initializationWritten } },
        )
        writer.observeWrittenInitializations()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun requestWithNetworkRetry(
        requestCoordinator: SabrRequestCoordinator,
        request: YoutubeSabrRequest,
        writer: SabrSegmentWriter,
        progressChecker: BooleanSupplier? = null,
    ) {
        var attempts = 0
        while (true) {
            try {
                if (progressChecker != null) {
                    requestCoordinator.request(
                        request,
                        { segment -> writer.acceptSegment(segment) },
                        progressChecker,
                    )
                } else {
                    requestCoordinator.request(request) { segment ->
                        writer.acceptSegment(segment)
                    }
                }
                return
            } catch (error: IOException) {
                if (!isRetryableNetworkError(error) || attempts >= MAX_REQUEST_NETWORK_RETRIES) {
                    throw error
                }
                attempts++
                Thread.sleep(REQUEST_NETWORK_RETRY_DELAY_MS)
                ensureRunning()
            }
        }
    }

    private fun isRetryableNetworkError(error: IOException): Boolean {
        return error is SocketTimeoutException ||
            error is ConnectException ||
            error is UnknownHostException ||
            error is SocketException
    }

    private fun downloadPlayerTimeMs(targets: List<SabrDownloadTarget>): Long {
        return targets.minOf { target ->
            if (target.nextWriteSequence <= 1) 0L
            else target.timeline?.getEndMs(target.nextWriteSequence - 1) ?: 0L
        }
    }

    private fun isDownloadComplete(targets: List<SabrDownloadTarget>): Boolean {
        return targets.all { target ->
            val endSequence = target.timeline?.endSequence ?: Int.MAX_VALUE
            target.pending.isEmpty() && target.nextWriteSequence > endSequence
        }
    }

    private fun completeMission(finalBytes: Long) {
        if (finalBytes > 0) {
            mission.done = finalBytes
            mission.length = finalBytes
        }
        mission.current = mission.urls.size
        mission.psState = 2
        mission.sabrCheckpoint = null
        cleanup(mission)
        mission.unknownLength = false
        mission.notifyFinished()
    }

    @Throws(InterruptedException::class)
    private fun ensureRunning() {
        if (!mission.running || Thread.currentThread().isInterrupted) {
            throw InterruptedException()
        }
    }

    private fun notifyErrorAndCleanup(error: Exception) {
        if (mission.running) {
            mission.notifyError(error)
        }
    }

    private fun classifyProtocolException(error: SabrProtocolException): SabrDownloadException {
        val message = error.message.orEmpty()
        val reason = when {
            message.contains("protected", ignoreCase = true) ||
                message.contains("PO token", ignoreCase = true) -> {
                SabrDownloadException.Reason.PROTECTED
            }
            message.contains("policy-only", ignoreCase = true) ||
                message.contains("not returned", ignoreCase = true) ||
                message.contains("integrity", ignoreCase = true) -> {
                SabrDownloadException.Reason.STALLED
            }
            else -> SabrDownloadException.Reason.PROTOCOL
        }
        return SabrDownloadException(
            reason,
            "SABR download failed: ${message.ifBlank { "protocol error" }}",
            error,
        )
    }

    companion object {
        private const val IDLE_POLL_MS = 250L
        private const val MAX_NO_PROGRESS_RESPONSES = 60
        private const val MAX_REQUEST_NETWORK_RETRIES = 2
        private const val REQUEST_NETWORK_RETRY_DELAY_MS = 1_000L
        private const val MAX_INITIALIZATION_BYTES = 16 * 1024 * 1024

        @JvmStatic
        fun cleanup(mission: DownloadMission) {
            try {
                workDirectory(mission).deleteRecursively()
            } catch (ignored: Exception) {
                // Nothing to do.
            }
        }

        private fun workDirectory(mission: DownloadMission): File {
            val base = mission.context.getExternalFilesDir(null) ?: mission.context.filesDir
            val missionId = if (mission.timestamp > 0) {
                mission.timestamp.toString()
            } else {
                mission.storage.name.hashCode().toString()
            }
            return File(base, "sabr-downloader/$missionId")
        }
    }

}
