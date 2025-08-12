package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.SilenceSkippingAudioProcessor;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

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

    private final boolean alwaysUseExoplayerSetOutputSurfaceWorkaround;
    private final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;

    public CustomRenderersFactory(
            final Context context,
            final boolean alwaysUseExoplayerSetOutputSurfaceWorkaround,
            final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor) {
        super(context);
        this.alwaysUseExoplayerSetOutputSurfaceWorkaround =
                alwaysUseExoplayerSetOutputSurfaceWorkaround;
        this.silenceSkippingAudioProcessor = silenceSkippingAudioProcessor;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    protected void buildVideoRenderers(final Context context,
                                       @ExtensionRendererMode final int extensionRendererMode,
                                       final MediaCodecSelector mediaCodecSelector,
                                       final boolean enableDecoderFallback,
                                       final Handler eventHandler,
                                       final VideoRendererEventListener eventListener,
                                       final long allowedVideoJoiningTimeMs,
                                       final ArrayList<Renderer> out) {
        if (alwaysUseExoplayerSetOutputSurfaceWorkaround) {
            out.add(new CustomMediaCodecVideoRenderer(context, getCodecAdapterFactory(),
                    mediaCodecSelector, allowedVideoJoiningTimeMs, enableDecoderFallback,
                    eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
        } else {
            super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector,
                    enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs,
                    out);
        }
    }

    @Override
    protected AudioSink buildAudioSink(
            final Context context,
            final boolean enableFloatOutput,
            final boolean enableAudioTrackPlaybackParams,
            final boolean enableOffload) {
        return new DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setOffloadMode(
                        enableOffload
                                ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                                : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                .setAudioProcessorChain(new DefaultAudioSink.DefaultAudioProcessorChain(
                        new AudioProcessor[]{}, silenceSkippingAudioProcessor,
                        new SonicAudioProcessor()
                ))
                .build();
    }
}
