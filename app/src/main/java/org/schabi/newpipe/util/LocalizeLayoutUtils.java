package org.schabi.newpipe.util;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

public final class LocalizeLayoutUtils {
    private LocalizeLayoutUtils() { }

    private static Boolean isRTL = null;

    public static int getLayoutPosition(final boolean isRtl, final int count, final int position) {
        return isRtl
                ? count - 1 - position
                : position;
    }

    public static boolean isRTL(final Context context) {
        if (isRTL != null) {
            return isRTL;
        }

        final Configuration config = context.getResources().getConfiguration();

        isRTL = config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        return isRTL;
    }
}
