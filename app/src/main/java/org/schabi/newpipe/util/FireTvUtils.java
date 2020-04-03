package org.schabi.newpipe.util;

import org.schabi.newpipe.App;

public final class FireTvUtils {
    private FireTvUtils() { }

    public static boolean isFireTv() {
        return App.getApp().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
    }
}
