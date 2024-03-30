package org.schabi.newpipe.player.helper

import android.content.Context
import android.os.Handler
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.video.VideoRendererEventListener

/**
 * A [DefaultRenderersFactory] which only uses [CustomMediaCodecVideoRenderer] as an
 * implementation of video codec renders.
 *
 *
 *
 * As no ExoPlayer extension is currently used, the reflection code used by ExoPlayer to try to
 * load video extension libraries is not needed in our case and has been removed. This should be
 * changed in the case an extension is shipped with the app, such as the AV1 one.
 *
 */
class CustomRenderersFactory(context: Context?) : DefaultRenderersFactory((context)!!) {
    override fun buildVideoRenderers(context: Context,
                                     extensionRendererMode: @ExtensionRendererMode Int,
                                     mediaCodecSelector: MediaCodecSelector,
                                     enableDecoderFallback: Boolean,
                                     eventHandler: Handler,
                                     eventListener: VideoRendererEventListener,
                                     allowedVideoJoiningTimeMs: Long,
                                     out: ArrayList<Renderer>) {
        out.add(CustomMediaCodecVideoRenderer(context, getCodecAdapterFactory(),
                mediaCodecSelector, allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler,
                eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY))
    }
}
