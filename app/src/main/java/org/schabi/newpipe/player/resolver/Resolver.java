package org.schabi.newpipe.player.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.player.playback.CustomTrackSelector;

public interface Resolver<Source, Product> {
    @Nullable
    Product resolve(@NonNull CustomTrackSelector trackSelector,
                    @NonNull Source source);
}
