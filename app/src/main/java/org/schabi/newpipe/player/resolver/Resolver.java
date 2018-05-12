package org.schabi.newpipe.player.resolver;

import android.support.annotation.NonNull;

public interface Resolver<Source, Product> {
    Product resolve(@NonNull Source source);
}
