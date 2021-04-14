package org.schabi.newpipe.player.renderer;

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

public class NewPipeRenderersFactory extends DefaultRenderersFactory {
    /**
     * @param context A {@link Context}.
     */
    public NewPipeRenderersFactory(Context context) {
        super(context);
    }

    /**
     * Builds video renderers for use by the player.
     *
     * @param context                   The {@link Context} associated with the player.
     * @param extensionRendererMode     The extension renderer mode.
     * @param mediaCodecSelector        A decoder selector.
     * @param enableDecoderFallback     Whether to enable fallback to lower-priority decoders if decoder
     *                                  initialization fails. This may result in using a decoder that is slower/less efficient than
     *                                  the primary decoder.
     * @param eventHandler              A handler associated with the main thread's looper.
     * @param eventListener             An event listener.
     * @param allowedVideoJoiningTimeMs The maximum duration for which video renderers can attempt to
     *                                  seamlessly join an ongoing playback, in milliseconds.
     * @param out                       An array to which the built renderers should be appended.
     */
    protected void buildVideoRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            long allowedVideoJoiningTimeMs,
            ArrayList<Renderer> out) {
        NewPipeVideoRenderer videoRenderer =
                new NewPipeVideoRenderer(
                        context,
                        mediaCodecSelector,
                        allowedVideoJoiningTimeMs,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
        out.add(videoRenderer);
    }
}