package org.schabi.newpipe.util;

public interface Function<I, O> {
    O apply(I input);
}
