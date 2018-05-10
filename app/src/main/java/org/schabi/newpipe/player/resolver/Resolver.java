package org.schabi.newpipe.player.resolver;

import android.support.annotation.NonNull;

public interface Resolver<Source, Produce> {
    Produce resolve(@NonNull Source source);
}
