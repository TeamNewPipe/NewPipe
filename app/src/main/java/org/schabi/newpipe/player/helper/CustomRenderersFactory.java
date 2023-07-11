package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.util.ArrayList;

/**
 * A {@link DefaultRenderersFactory} which only uses {@link CustomMediaCodecVideoRenderer} as an
 * implementation of video codec renders.
 *
 * <p>
 * As no ExoPlayer extension is currently used, the reflection code used by ExoPlayer to try to
 * load video extension libraries is not needed in our case and has been removed. This should be
 * changed in the case an extension is shipped with the app, such as the AV1 one.
 * </p>
 */
public final class CustomRenderersFactory extends DefaultRenderersFactory {

    public CustomRenderersFactory(final Context context) {
        super(context);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    protected void buildVideoRenderers(@NonNull final Context context,
                                       @ExtensionRendererMode final int extensionRendererMode,
                                       @NonNull final MediaCodecSelector mediaCodecSelector,
                                       final boolean enableDecoderFallback,
                                       @NonNull final Handler eventHandler,
                                       @NonNull final VideoRendererEventListener eventListener,
                                       final long allowedVideoJoiningTimeMs,
                                       final ArrayList<Renderer> out) {
        out.add(new CustomMediaCodecVideoRenderer(context, getCodecAdapterFactory(),
                mediaCodecSelector, allowedVideoJoiningTimeMs, enableDecoderFallback, eventHandler,
                eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
    }
}
