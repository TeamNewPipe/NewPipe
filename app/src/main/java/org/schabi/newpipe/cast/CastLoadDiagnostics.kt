/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.cast

import androidx.annotation.StringRes
import java.io.IOException
import org.fcast.sender_sdk.CastingDeviceException
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.ReceiverError
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction

/** One bounded report set per load attempt. Receiver messages and source URLs are never retained. */
internal class CastLoadDiagnostics(
    private val protocol: ProtocolType,
    private val source: SourceKind,
    contentType: String,
    private val serviceId: Int,
    private val report: (ErrorInfo, Int) -> Unit
) {
    internal enum class SourceKind { HLS, PROGRESSIVE, DASH, AUDIO }
    private enum class Stage { CONNECT, LOAD, PLAYBACK, COMMAND }
    private val mime = when (contentType.substringBefore(';').trim().lowercase()) {
        "application/vnd.apple.mpegurl", "application/x-mpegurl" -> "HLS"
        "application/dash+xml" -> "DASH"
        "video/mp4", "audio/mp4" -> "MP4"
        "video/webm", "audio/webm" -> "WEBM"
        "audio/mpeg" -> "MP3"
        "audio/ogg", "video/ogg", "application/ogg" -> "OGG"
        else -> "OTHER"
    }
    private val reported = mutableSetOf<Pair<Stage, String>>()

    @Synchronized
    fun beginLoad() = reported.clear()

    fun connectionFailed(error: Throwable) = failure(Stage.CONNECT, kind(error), R.string.cast_connection_report_failed)
    fun loadFailed(error: Throwable) = failure(Stage.LOAD, kind(error), R.string.cast_playback_failed)
    fun playbackFailed() = failure(Stage.PLAYBACK, "RECEIVER_PLAYBACK_ERROR", R.string.cast_playback_failed)
    fun commandFailed(error: ReceiverError) = failure(Stage.COMMAND, error.name, R.string.cast_receiver_command_failed)

    private fun failure(stage: Stage, kind: String, @StringRes message: Int) {
        synchronized(this) {
            if (reported.size >= MAX_REPORTS_PER_ATTEMPT || !reported.add(stage to kind)) return
        }
        val diagnostic = "protocol=${protocol.name} source=$source mime=$mime stage=$stage failure=$kind"
        report(
            ErrorInfo(
                arrayOf("Casting diagnostic: $diagnostic"),
                UserAction.PLAY_STREAM,
                "Cast playback [$diagnostic]",
                serviceId,
                message
            ),
            message
        )
    }

    private fun kind(error: Throwable): String = when (error) {
        is CastingDeviceException.FailedToSendCommand -> "FAILED_TO_SEND_COMMAND"
        is CastingDeviceException.MissingAddresses -> "MISSING_ADDRESSES"
        is CastingDeviceException.DeviceAlreadyStarted -> "DEVICE_ALREADY_STARTED"
        is CastingDeviceException.UnsupportedFeature -> "UNSUPPORTED_FEATURE"
        is IOException -> "IO_ERROR"
        is SecurityException -> "PERMISSION_ERROR"
        is IllegalArgumentException -> "INVALID_ARGUMENT"
        is IllegalStateException -> "INVALID_STATE"
        else -> "UNKNOWN"
    }

    private companion object {
        const val MAX_REPORTS_PER_ATTEMPT = 3
    }
}
