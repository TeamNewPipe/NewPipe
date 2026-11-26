package org.schabi.newpipe.cast

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.CopyOnWriteArraySet
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceDiscovererEventHandler
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaTrack
import org.fcast.sender_sdk.MediaTrackType
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.QueueState
import org.fcast.sender_sdk.ReceiverError
import org.fcast.sender_sdk.Source
import org.fcast.sender_sdk.TrackList
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.CastLoadDiagnostics.SourceKind
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.external_communication.TubeBeamUtils
import org.schabi.newpipe.util.external_communication.YtFifoUtils

/** Application-scoped discovery and playback handoff for FCast-compatible TV receivers. */
object FCastManager {
    private const val TAG = "FCastManager"
    private const val RECEIVER_URL = "https://fcast.org"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val castContext by lazy { CastContext() }
    private val devices = linkedMapOf<String, DeviceInfo>()
    private val observers = CopyOnWriteArraySet<(List<DeviceInfo>) -> Unit>()
    private val sessionObservers = CopyOnWriteArraySet<() -> Unit>()
    private var discoverer: NsdDeviceDiscoverer? = null

    @Volatile
    private var activeDevice: CastingDevice? = null
    private var activeHandler: DeviceEventHandler? = null

    @Volatile
    private var activeDeviceName: String? = null

    @Volatile
    private var activePlaying = false

    @JvmStatic
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /** Local timeline actions must not imply that they control the connected receiver. */
    fun isCasting(): Boolean = activeDevice?.isReady() == true

    /** Observe receiver connection changes without retaining a player UI after it is destroyed. */
    fun observeSession(listener: () -> Unit): AutoCloseable {
        sessionObservers.add(listener)
        return AutoCloseable { sessionObservers.remove(listener) }
    }

    private fun notifySessionChanged() {
        mainHandler.post { sessionObservers.forEach { it() } }
    }

    @JvmStatic
    fun canCast(context: Context, info: StreamInfo): Boolean = isAvailable() && selectSource(context, info) != null

    /** Whether TubeBeam is installed and can forward this stream to SmartTube on an Android TV. */
    @JvmStatic
    fun canSendToTv(context: Context, info: StreamInfo): Boolean = TubeBeamUtils.shouldShowSendToTv(context, info.serviceId)

    /** Whether a yt-fifo server is configured, so the stream's audio can be played on the home audio system. */
    @JvmStatic
    fun canSendToHomeAudio(context: Context): Boolean = YtFifoUtils.shouldShowHomeAudio(context)

    @JvmStatic
    fun showDevicePicker(context: Context, info: StreamInfo, localPlayer: Player?) {
        if (!isAvailable() && !canSendToTv(context, info) && !canSendToHomeAudio(context)) return
        val connectedDevice = activeDevice
        val connectedName = activeDeviceName
        if (connectedDevice?.isReady() == true && connectedName != null) {
            showActiveControls(context, info, connectedDevice, connectedName)
            return
        }
        showAvailableDevices(context, info, localPlayer)
    }

    private fun homeAudioRows(context: Context, info: StreamInfo): List<FixedRow> {
        if (!canSendToHomeAudio(context)) return emptyList()
        return listOf(
            FixedRow(context.getString(R.string.play_on_home_audio_title)) {
                YtFifoUtils.sendToHomeAudio(context, info.url, true)
            },
            FixedRow(context.getString(R.string.queue_on_home_audio_title)) {
                YtFifoUtils.sendToHomeAudio(context, info.url, false)
            }
        )
    }

    private fun showAvailableDevices(context: Context, info: StreamInfo, localPlayer: Player?) {
        val source = if (isAvailable()) selectSource(context, info) else null
        // External handoffs (TubeBeam, home audio) are fixed rows at the top of the list; discovered
        // receivers follow them.
        val tubeBeam = canSendToTv(context, info)
        val fixedRows = mutableListOf<FixedRow>()
        if (tubeBeam) {
            fixedRows += FixedRow(context.getString(R.string.send_to_tv_title)) { sendToTubeBeam(context, info, localPlayer) }
        }
        fixedRows.addAll(homeAudioRows(context, info))
        if (source == null && fixedRows.isEmpty()) {
            Toast.makeText(context, R.string.cast_no_compatible_stream, Toast.LENGTH_LONG).show()
            return
        }

        if (source != null) startDiscovery(context.applicationContext)
        val availableDevices = mutableListOf<DeviceInfo>()
        val labels = mutableListOf<String>()
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, labels)
        lateinit var observer: (List<DeviceInfo>) -> Unit
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cast_to_tv)
            .setAdapter(adapter) { _, position ->
                if (position < fixedRows.size) {
                    fixedRows[position].action()
                } else if (source != null) {
                    availableDevices.getOrNull(position - fixedRows.size)?.let {
                        connect(context, it, source, createPlaybackHandoff(info, localPlayer))
                    }
                }
            }
            .setNeutralButton(R.string.cast_receiver_help) { _, _ ->
                if (!tubeBeam) {
                    ShareUtils.openUrlInApp(context, RECEIVER_URL)
                } else {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.cast_receiver_help)
                        .setMessage(R.string.tubebeam_setup_help)
                        .setPositiveButton(R.string.tubebeam_app_name) { _, _ ->
                            ShareUtils.openUrlInApp(context, "https://github.com/jagjordi/tubebeam#tv-setup-once-per-tv")
                        }
                        .setNeutralButton(R.string.tubebeam_fcast_help) { _, _ ->
                            ShareUtils.openUrlInApp(context, RECEIVER_URL)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        observer = { currentDevices ->
            availableDevices.clear()
            if (source != null) availableDevices.addAll(currentDevices.filter(::isResolved))
            labels.clear()
            labels.addAll(fixedRows.map { it.label })
            labels.addAll(availableDevices.map(::deviceLabel))
            adapter.notifyDataSetChanged()
            dialog.setTitle(
                if (source != null && availableDevices.isEmpty()) {
                    R.string.cast_searching
                } else {
                    R.string.cast_to_tv
                }
            )
        }
        observers.add(observer)
        dialog.setOnDismissListener { observers.remove(observer) }
        dialog.show()
        observer(snapshot())
    }

    @Synchronized
    private fun startDiscovery(context: Context) {
        if (discoverer != null) return
        discoverer =
            NsdDeviceDiscoverer(
                context,
                object : DeviceDiscovererEventHandler {
                    override fun deviceAvailable(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                    override fun deviceChanged(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                    override fun deviceRemoved(deviceName: String) {
                        synchronized(this@FCastManager) {
                            devices.remove(deviceName)
                        }
                        publishDevices()
                    }
                }
            )
    }

    private fun updateDevice(deviceInfo: DeviceInfo) {
        synchronized(this) {
            devices[deviceInfo.name] = deviceInfo
        }
        publishDevices()
    }

    private fun publishDevices() {
        val current = snapshot()
        mainHandler.post { observers.forEach { it(current) } }
    }

    @Synchronized
    private fun snapshot(): List<DeviceInfo> = devices.values.sortedBy { it.name.lowercase() }

    private fun connect(
        context: Context,
        deviceInfo: DeviceInfo,
        source: CastSource,
        handoff: PlaybackHandoff?
    ) {
        val applicationContext = context.applicationContext
        val diagnostics = CastLoadDiagnostics(deviceInfo.protocol, source.kind, source.contentType, source.serviceId) { report, message ->
            Log.e(TAG, report.request)
            mainHandler.post { ErrorUtil.createNotification(applicationContext, report, message) }
        }
        Toast.makeText(
            context,
            context.getString(R.string.cast_connecting, deviceInfo.name),
            Toast.LENGTH_SHORT
        ).show()
        runCatching {
            activeDevice?.disconnect()
            val device = castContext.createDeviceFromInfo(deviceInfo)
            val handler = playbackHandler(
                context.applicationContext,
                device,
                deviceInfo,
                source,
                handoff,
                diagnostics
            )
            activeDevice = device
            activeDeviceName = deviceInfo.name
            activePlaying = false
            activeHandler = handler
            device.connect(
                ApplicationInfo(
                    "WizeStream",
                    BuildConfig.VERSION_NAME,
                    "${Build.MANUFACTURER} ${Build.MODEL}"
                ),
                handler,
                1000uL
            )
        }.onFailure {
            handoff?.cancel()
            clearActiveDevice()
            diagnostics.connectionFailed(it)
        }
    }

    private fun showActiveControls(
        context: Context,
        info: StreamInfo,
        device: CastingDevice,
        deviceName: String
    ) {
        val playPauseLabel = if (activePlaying) R.string.pause else R.string.play
        val homeAudio = homeAudioRows(context, info)
        val actions = arrayOf(
            context.getString(playPauseLabel),
            context.getString(R.string.stop_casting),
            context.getString(R.string.change_tv)
        ) + homeAudio.map { it.label }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.casting_to, deviceName))
            .setItems(actions) { _, position ->
                when (position) {
                    0 -> if (activePlaying) device.pausePlayback() else device.resumePlayback()

                    1 -> runCatching {
                        device.stopPlayback()
                        device.disconnect()
                    }.also { clearActiveDevice() }

                    2 -> {
                        runCatching { device.disconnect() }
                        clearActiveDevice()
                        showAvailableDevices(context, info, null)
                    }

                    else -> homeAudio.getOrNull(position - 3)?.action?.invoke()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    internal fun playbackHandler(
        context: Context,
        device: CastingDevice,
        deviceInfo: DeviceInfo,
        source: CastSource,
        handoff: PlaybackHandoff?,
        diagnostics: CastLoadDiagnostics
    ): DeviceEventHandler = object : DeviceEventHandler {
        private var pendingHandoff = handoff

        override fun connectionStateChanged(state: DeviceConnectionState) {
            when (state) {
                is DeviceConnectionState.Connected -> {
                    showToast(context, context.getString(R.string.cast_connected, deviceInfo.name))
                    mainHandler.post {
                        if (activeDevice !== device) {
                            cancelHandoff()
                            return@post
                        }
                        diagnostics.beginLoad()
                        runCatching {
                            device.load(
                                LoadRequest.Video(
                                    contentType = source.contentType,
                                    url = source.url,
                                    resumePosition = pendingHandoff?.resumePositionSeconds() ?: 0.0,
                                    speed = null,
                                    volume = null,
                                    metadata = null,
                                    requestHeaders = null
                                ),
                                500uL
                            )
                        }.onFailure {
                            cancelHandoff()
                            diagnostics.loadFailed(it)
                        }
                    }
                }

                DeviceConnectionState.Disconnected -> {
                    cancelHandoff()
                    if (activeDevice === device) clearActiveDevice()
                }

                DeviceConnectionState.Connecting,
                DeviceConnectionState.Reconnecting -> Unit
            }
            if (activeDevice === device) notifySessionChanged()
        }

        override fun volumeChanged(volume: Double) = Unit
        override fun timeChanged(time: Double) = Unit
        override fun playbackStateChanged(state: PlaybackState) {
            if (activeDevice === device) {
                activePlaying = state == PlaybackState.PLAYING
                if (activePlaying) confirmHandoff()
            }
        }
        override fun durationChanged(duration: Double) = Unit
        override fun speedChanged(speed: Double) = Unit
        override fun sourceChanged(source: Source) = Unit
        override fun playbackError(message: String) {
            cancelHandoff()
            if (activeDevice === device) diagnostics.playbackFailed()
        }
        override fun tracksAvailable(tracks: List<MediaTrack>) = Unit
        override fun trackSelected(id: UInt?, typ: MediaTrackType) = Unit
        override fun playbackStopped() = cancelHandoff()
        override fun tracksChanged(tracks: TrackList) = Unit
        override fun queueChanged(queue: QueueState) = Unit
        override fun commandError(error: ReceiverError) {
            cancelHandoff()
            if (activeDevice === device) diagnostics.commandFailed(error)
        }

        private fun confirmHandoff() {
            val currentHandoff = synchronized(this) {
                pendingHandoff.also { pendingHandoff = null }
            } ?: return
            mainHandler.post {
                if (activeDevice === device) {
                    currentHandoff.confirmRemotePlayback()
                } else {
                    currentHandoff.cancel()
                }
            }
        }

        private fun cancelHandoff() {
            synchronized(this) {
                pendingHandoff?.cancel()
                pendingHandoff = null
            }
        }
    }

    /** TubeBeam cannot confirm remote playback: cancellation and failures must leave audio alone. */
    internal fun sendToTubeBeam(context: Context, info: StreamInfo, localPlayer: Player?): Boolean {
        val position = if (localPlayer != null && isSameStream(localPlayer, info) &&
            !StreamTypeUtil.isLiveStream(info.streamType) && !localPlayer.exoPlayerIsNull()
        ) {
            localPlayer.exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            null
        }
        return TubeBeamUtils.sendToTv(context, info.url, position)
    }

    private fun createPlaybackHandoff(info: StreamInfo, player: Player?): PlaybackHandoff? {
        if (player == null || !isSameStream(player, info)) return null

        return PlaybackHandoff(
            resumePositionProvider = {
                if (!StreamTypeUtil.isLiveStream(info.streamType) &&
                    isSameStream(player, info) &&
                    !player.exoPlayerIsNull()
                ) {
                    player.exoPlayer.currentPosition.coerceAtLeast(0L) / 1000.0
                } else {
                    0.0
                }
            },
            remotePlaybackStarted = {
                if (isSameStream(player, info) && player.isPlaying) player.pause()
            }
        )
    }

    private fun isSameStream(player: Player, info: StreamInfo): Boolean = player.currentStreamInfo
        .filter { current ->
            current.serviceId == info.serviceId && current.url == info.url
        }
        .isPresent

    private fun selectSource(context: Context, info: StreamInfo): CastSource? {
        val hlsUrl: String? = info.hlsUrl
        if (!hlsUrl.isNullOrBlank()) {
            return CastSource(hlsUrl, "application/vnd.apple.mpegurl", SourceKind.HLS, info.serviceId)
        }

        val videoStreams = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .toMutableList()
        if (videoStreams.isNotEmpty()) {
            val index = ListHelper.getDefaultResolutionIndex(context, videoStreams)
                .coerceIn(0, videoStreams.lastIndex)
            return sourceFrom(videoStreams[index], info.serviceId)
        }

        val dashMpdUrl: String? = info.dashMpdUrl
        if (!dashMpdUrl.isNullOrBlank()) {
            return CastSource(dashMpdUrl, "application/dash+xml", SourceKind.DASH, info.serviceId)
        }

        return info.audioStreams.firstOrNull { it.isUrl }?.let { sourceFrom(it, info.serviceId) }
    }

    private fun sourceFrom(stream: VideoStream, serviceId: Int): CastSource = CastSource(
        stream.content,
        stream.format?.mimeType ?: "video/mp4",
        SourceKind.PROGRESSIVE,
        serviceId
    )

    private fun sourceFrom(stream: AudioStream, serviceId: Int): CastSource = CastSource(
        stream.content,
        stream.format?.mimeType ?: "audio/mp4",
        SourceKind.AUDIO,
        serviceId
    )

    private fun isResolved(deviceInfo: DeviceInfo): Boolean = deviceInfo.port != 0.toUShort() && deviceInfo.addresses.isNotEmpty()

    private fun deviceLabel(deviceInfo: DeviceInfo): String {
        val protocol = if (deviceInfo.protocol == ProtocolType.F_CAST) "FCast" else "Cast"
        return "${deviceInfo.name} · $protocol"
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    @Synchronized
    private fun clearActiveDevice() {
        activeDevice = null
        activeDeviceName = null
        activeHandler = null
        activePlaying = false
        notifySessionChanged()
    }

    internal data class CastSource(val url: String, val contentType: String, val kind: SourceKind, val serviceId: Int)

    /** A picker entry that is not a discovered receiver. */
    private class FixedRow(val label: String, val action: () -> Unit)
}
