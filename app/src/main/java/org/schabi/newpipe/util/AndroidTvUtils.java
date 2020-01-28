package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;

import android.view.KeyEvent;
import org.schabi.newpipe.App;

public class AndroidTvUtils {
    @SuppressLint("InlinedApi")
    public static boolean isTv(){
        final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

        PackageManager pm =  App.getApp().getPackageManager();

        return pm.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean isConfirmKey(int keyCode) {
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
