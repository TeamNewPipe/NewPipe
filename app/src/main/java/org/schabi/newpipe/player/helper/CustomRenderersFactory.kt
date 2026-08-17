@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * A [DefaultRenderersFactory] which only uses [CustomMediaCodecVideoRenderer] as an
 * implementation of video codec renders.
 *
 * <p>
 * As no ExoPlayer extension is currently used, the reflection code used by ExoPlayer to try to
 * load video extension libraries is not needed in our case and has been removed. This should be
 * changed in the case an extension is shipped with the app, such as the AV1 one.
 * </p>
 */
class CustomRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(
            CustomMediaCodecVideoRenderer(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
            )
        )
    }
}
