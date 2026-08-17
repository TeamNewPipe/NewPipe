@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * A [MediaCodecVideoRenderer] which always enable the output surface workaround that
 * ExoPlayer enables on several devices which are known to implement
 * [android.media.MediaCodec.setOutputSurface]
 * MediaCodec.setOutputSurface(Surface) incorrectly.
 *
 * <p>
 * See [MediaCodecVideoRenderer.codecNeedsSetOutputSurfaceWorkaround] for more
 * details.
 * </p>
 *
 * <p>
 * This custom [MediaCodecVideoRenderer] may be useful in the case a device is affected by
 * this issue but is not present in ExoPlayer's list.
 * </p>
 *
 * <p>
 * This class has only effect on devices with Android 6 and higher, as the {@code setOutputSurface}
 * method is only implemented in these Android versions and the method used as a workaround is
 * always applied on older Android versions (releasing and re-instantiating video codec instances).
 * </p>
 */
class CustomMediaCodecVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {

    override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean {
        return true
    }
}
