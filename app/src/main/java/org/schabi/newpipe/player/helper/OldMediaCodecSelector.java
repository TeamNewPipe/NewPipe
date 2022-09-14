package org.schabi.newpipe.player.helper;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;

import java.util.Collections;
import java.util.List;

public final class OldMediaCodecSelector implements MediaCodecSelector {

    public static final MediaCodecSelector INSTANCE = new OldMediaCodecSelector();

    @NonNull
    @Override
    public List<MediaCodecInfo> getDecoderInfos(@NonNull final String mimeType,
                                                final boolean requiresSecureDecoder,
                                                final boolean requiresTunnelingDecoder)
            throws MediaCodecUtil.DecoderQueryException {
        final List<MediaCodecInfo> codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder);
        if (codecs.isEmpty()) {
            return Collections.emptyList();
        }

        // Only consider the first decoder.
        return Collections.singletonList(codecs.get(0));
    }
}
