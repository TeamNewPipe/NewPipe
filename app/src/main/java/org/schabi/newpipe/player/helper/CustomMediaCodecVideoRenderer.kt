package org.schabi.newpipe.player.helper

import android.content.Context
import android.os.Handler
import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener

/**
 * A [MediaCodecVideoRenderer] which always enable the output surface workaround that
 * ExoPlayer enables on several devices which are known to implement
 * [ MediaCodec.setOutputSurface(Surface)][android.media.MediaCodec.setOutputSurface] incorrectly.
 *
 *
 *
 * See [MediaCodecVideoRenderer.codecNeedsSetOutputSurfaceWorkaround] for more
 * details.
 *
 *
 *
 *
 * This custom [MediaCodecVideoRenderer] may be useful in the case a device is affected by
 * this issue but is not present in ExoPlayer's list.
 *
 *
 *
 *
 * This class has only effect on devices with Android 6 and higher, as the `setOutputSurface`
 * method is only implemented in these Android versions and the method used as a workaround is
 * always applied on older Android versions (releasing and re-instantiating video codec instances).
 *
 */
class CustomMediaCodecVideoRenderer(context: Context?,
                                    codecAdapterFactory: MediaCodecAdapter.Factory?,
                                    mediaCodecSelector: MediaCodecSelector?,
                                    allowedJoiningTimeMs: Long,
                                    enableDecoderFallback: Boolean,
                                    eventHandler: Handler?,
                                    eventListener: VideoRendererEventListener?,
                                    maxDroppedFramesToNotify: Int) : MediaCodecVideoRenderer((context)!!, (codecAdapterFactory)!!, (mediaCodecSelector)!!, allowedJoiningTimeMs,
        enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify) {
    override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean {
        return true
    }
}
