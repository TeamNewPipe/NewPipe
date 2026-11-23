/* SPDX-License-Identifier: GPL-3.0-or-later */
package us.shandian.giga.get

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import org.schabi.newpipe.download.artwork.AudioArtworkTags
import org.schabi.newpipe.download.artwork.DownloadArtworkImages
import org.schabi.newpipe.streams.io.SharpStream

/** Optional final stage for every supported audio route, including files that need no conversion. */
object AudioArtworkFinalizer {
    @JvmStatic
    fun captureSourceCover(mission: DownloadMission) {
        val request = mission.artworkRequest ?: return
        if (request.capturedSourceCover || mission.isCancellationRequested) return
        request.sourceCover = runCatching {
            openDestination(mission).use { source ->
                val input = SharpSource(source, mission.offsets.firstOrNull() ?: 0)
                AudioArtworkTags.detectFormat(input)?.let { AudioArtworkTags.readCover(input, it) }
            }
        }.getOrNull()?.takeIf { DownloadArtworkImages.dimensions(it) != null }
        request.capturedSourceCover = true
        if (!mission.deleted) mission.writeThisToFile()
    }

    @JvmStatic
    fun finish(mission: DownloadMission) {
        try {
            apply(mission)
            if (mission.isCancellationRequested) return
            mission.artworkRequest?.completed = true
            mission.length = mission.storage.length()
            mission.done = mission.length
            mission.writeThisToFile()
            mission.notifyFinished()
        } catch (_: InterruptedIOException) {
            // The normal pause/cancel operation persists the mission. No completion is published.
        } catch (error: IOException) {
            // Only a failed output restoration reaches here. Keep its durable backup for retry.
            if (!mission.deleted) mission.notifyError(DownloadMission.ERROR_FILE_CREATION, error)
        }
    }

    private fun apply(mission: DownloadMission) {
        val request = mission.artworkRequest ?: return
        val metadata = mission.metadata ?: return
        val directory = File(metadata.parentFile, "artwork").apply { mkdirs() }
        if (!directory.isDirectory) return
        val original = File(directory, metadata.name + ".original")
        val tagged = File(directory, metadata.name + ".tagged")
        val journal = File(directory, metadata.name + ".writing")
        try {
            if (mission.deleted && mission.deleteFileOnCancel) throw InterruptedIOException()
            if (journal.exists()) {
                if (!original.isFile || journal.readText() != mission.storage.uri.toString()) throw IOException("Audio artwork recovery is unavailable")
                restore(mission, original)
                if (!journal.delete()) throw IOException("Cannot finish audio artwork recovery")
            }
            if (mission.isCancellationRequested) throw InterruptedIOException()
            val cover = runCatching { openDestination(mission).use { AudioArtworkTags.readCover(SharpSource(it), request.format) } }.getOrNull()
            if (cover != null) return
            val artwork = request.sourceCover ?: DownloadArtworkImages.fetch(request, mission::isCancellationRequested) ?: return
            if (mission.isCancellationRequested) throw InterruptedIOException()
            val length = mission.storage.length()
            if (length <= 0 || length > (directory.usableSpace - artwork.bytes.size) / 2) return

            // Before touching the destination, stage both a full original and a validated result.
            val prepared = runCatching {
                openDestination(mission).use { source ->
                    FileOutputStream(original).use { target ->
                        copy(source, target, mission::isCancellationRequested)
                        target.fd.sync()
                    }
                }
                if (!AudioArtworkTags.embed(original, tagged, request.format, artwork, mission::isCancellationRequested)) return@runCatching false
                val writtenCover = java.io.RandomAccessFile(tagged, "r").use { source ->
                    AudioArtworkTags.readCover(
                        object : AudioArtworkTags.Source {
                            override val length: Long get() = source.length()
                            override fun read(position: Long, size: Int): ByteArray = ByteArray(size).also {
                                source.seek(position)
                                source.readFully(it)
                            }
                        },
                        request.format
                    )
                }
                require(writtenCover?.bytes?.contentEquals(artwork.bytes) == true && writtenCover.mimeType == artwork.mimeType)
                require(original.length() == length)
                // A document edited externally while artwork was prepared is left alone.
                openDestination(mission).use { source -> require(digest(source).contentEquals(digest(original))) }
                true
            }.getOrDefault(false)
            if (mission.isCancellationRequested) throw InterruptedIOException()
            if (!prepared) return

            val journalReady = runCatching {
                FileOutputStream(journal).use {
                    it.write(mission.storage.uri.toString().toByteArray())
                    it.fd.sync()
                }
                true
            }.getOrDefault(false)
            if (!journalReady) {
                journal.delete()
                return
            }
            try {
                writeDestination(mission, tagged, honourCancellation = true)
                openDestination(mission).use { source -> check(digest(source).contentEquals(digest(tagged))) }
                if (!journal.delete()) throw IOException("Cannot finalize audio artwork")
            } catch (failure: Exception) {
                if (!mission.deleted || !mission.deleteFileOnCancel) restore(mission, original)
                if (journal.exists() && !journal.delete()) throw IOException("Cannot finish audio artwork rollback", failure)
                if (mission.isCancellationRequested) throw InterruptedIOException()
                // The original is restored. Artwork failure alone must not fail the audio download.
            }
        } finally {
            if (!journal.exists() || mission.deleted && mission.deleteFileOnCancel) {
                original.delete()
                tagged.delete()
                journal.delete()
            }
        }
    }

    private fun restore(mission: DownloadMission, original: File) {
        try {
            writeDestination(mission, original, honourCancellation = false)
            openDestination(mission, honourCancellation = false).use { if (!digest(it).contentEquals(digest(original))) throw IOException("Audio artwork rollback verification failed") }
        } catch (failure: Exception) {
            throw IOException("Audio artwork output could not be restored; original retained for retry", failure)
        }
    }

    private fun writeDestination(mission: DownloadMission, source: File, honourCancellation: Boolean) {
        if (honourCancellation && mission.isCancellationRequested) throw InterruptedIOException()
        FileInputStream(source).use { input ->
            openDestination(mission, truncate = true, honourCancellation = honourCancellation).use { target ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    if (honourCancellation && mission.isCancellationRequested) throw InterruptedIOException()
                    val read = input.read(buffer)
                    if (read < 0) break
                    target.write(buffer, 0, read)
                }
                target.flush()
            }
        }
    }

    private fun openDestination(mission: DownloadMission, truncate: Boolean = false, honourCancellation: Boolean = true): SharpStream = synchronized(mission) {
        // FileStream opens in read/write mode even for reads. Serialize every open with cancel()
        // so a verification or rollback cannot recreate a file after the user deletes it.
        // Keep the actual copy outside this short transition so cancellation remains responsive.
        if (mission.deleted && mission.deleteFileOnCancel || honourCancellation && mission.isCancellationRequested) throw InterruptedIOException()
        if (truncate) mission.storage.openAndTruncateStream() else mission.storage.stream
    }

    private class SharpSource(private val stream: SharpStream, private val offset: Long = 0) : AudioArtworkTags.Source {
        override val length: Long get() = stream.length() - offset
        override fun read(position: Long, size: Int): ByteArray {
            require(position >= 0 && size >= 0 && position <= length - size)
            stream.seek(position + offset)
            val bytes = ByteArray(size)
            var count = 0
            while (count < size) {
                val read = stream.read(bytes, count, size - count)
                require(read > 0)
                count += read
            }
            return bytes
        }
    }

    private fun copy(source: SharpStream, target: FileOutputStream, cancelled: () -> Boolean) {
        val buffer = ByteArray(128 * 1024)
        while (true) {
            if (cancelled()) throw InterruptedIOException()
            val count = source.read(buffer)
            if (count < 0) return
            target.write(buffer, 0, count)
        }
    }

    private fun digest(source: SharpStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) return digest.digest()
            digest.update(buffer, 0, count)
        }
    }

    private fun digest(source: File): ByteArray = FileInputStream(source).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest()
    }
}
