package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import android.view.KeyEvent;
import org.schabi.newpipe.App;

public final class AndroidTvUtils {
    private static final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

    private AndroidTvUtils() { }

    @SuppressLint("InlinedApi")
    public static boolean isTv() {
        PackageManager pm =  App.getApp().getPackageManager();

        return pm.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean isConfirmKey(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }
}
