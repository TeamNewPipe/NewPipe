package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.view.KeyEvent;

import org.schabi.newpipe.App;

import static android.content.Context.BATTERY_SERVICE;
import static android.content.Context.UI_MODE_SERVICE;

public final class AndroidTvUtils {
    private static final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

    private AndroidTvUtils() {
    }

    @SuppressLint("InlinedApi")
    public static boolean isTv(final Context context) {
        PackageManager pm = App.getApp().getPackageManager();


        // from doc: https://developer.android.com/training/tv/start/hardware.html#runtime-check
        boolean isAndroidTv = ((UiModeManager) context.getSystemService(UI_MODE_SERVICE))
                .getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        boolean isBatteryAbsent = ((BatteryManager) context.getSystemService(BATTERY_SERVICE))
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 0;

        return isAndroidTv
                || pm.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)

                //from https://stackoverflow.com/a/58932366
                || (isBatteryAbsent && !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                    && pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                    && pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET));
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
