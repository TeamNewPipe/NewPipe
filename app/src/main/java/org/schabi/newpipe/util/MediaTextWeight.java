package org.schabi.newpipe.util;

import android.widget.TextView;

/** Preserves each layout's original weight when the optional bold setting is disabled. */
public final class MediaTextWeight {
    private MediaTextWeight() { }

    public static void apply(final TextView view) {
        AppFonts.applyMediaWeight(view);
    }
}
