package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

/**
 * A {@link MediaCodecVideoRenderer} which always enable the output surface workaround that
 * ExoPlayer enables on several devices which are known to implement
 * {@link android.media.MediaCodec#setOutputSurface(android.view.Surface)
 * MediaCodec.setOutputSurface(Surface)} incorrectly.
 *
 * <p>
 * See {@link MediaCodecVideoRenderer#codecNeedsSetOutputSurfaceWorkaround(String)} for more
 * details.
 * </p>
 *
 * <p>
 * This custom {@link MediaCodecVideoRenderer} may be useful in the case a device is affected by
 * this issue but is not present in ExoPlayer's list.
 * </p>
 *
 * <p>
 * This class has only effect on devices with Android 6 and higher, as the {@code setOutputSurface}
 * method is only implemented in these Android versions and the method used as a workaround is
 * always applied on older Android versions (releasing and re-instantiating video codec instances).
 * </p>
 */
public final class CustomMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

    @SuppressWarnings({"checkstyle:ParameterNumber", "squid:S107"})
    public CustomMediaCodecVideoRenderer(final Context context,
                                         final MediaCodecAdapter.Factory codecAdapterFactory,
                                         final MediaCodecSelector mediaCodecSelector,
                                         final long allowedJoiningTimeMs,
                                         final boolean enableDecoderFallback,
                                         @Nullable final Handler eventHandler,
                                         @Nullable final VideoRendererEventListener eventListener,
                                         final int maxDroppedFramesToNotify) {
        super(context, codecAdapterFactory, mediaCodecSelector, allowedJoiningTimeMs,
                enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(final String name) {
        return true;
    }
}
