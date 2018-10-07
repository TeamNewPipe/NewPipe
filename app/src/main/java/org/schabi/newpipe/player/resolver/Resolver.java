package org.schabi.newpipe.player.resolver;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface Resolver<Source, Product> {
    @Nullable Product resolve(@NonNull Source source);
}
