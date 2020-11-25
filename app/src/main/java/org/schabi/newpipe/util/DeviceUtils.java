package org.schabi.newpipe.util;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;

public final class DeviceUtils {

    private static final String AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv";
    private static Boolean isTV = null;

    private DeviceUtils() {
    }

    /**
     * Checks if the screen layout was forced to the given layout.
     * @param context Application context
     * @param resId The resource ID of the wanted screen layout
     * @return true if the layout was forced, false if the layout was forced to something else,
     * and null if the screen layout is auto
     */
    private static Boolean layoutForcedTo(final Context context, final int resId) {
        final String screenLayoutKey = context.getString(R.string.screen_layout_key);
        final String autoKey = context.getString(R.string.screen_layout_auto_key);
        final String wantedKey = context.getString(resId);
        final String screenLayout = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(screenLayoutKey, autoKey);

        if (screenLayout.equals(wantedKey)) {
            return true;
        } else if (!screenLayout.equals(autoKey)) {
            return false;
        }

        return null;
    }

    public static boolean isTv(final Context context) {
        if (isTV != null) {
            return isTV;
        }

        final Boolean forcedToTv = layoutForcedTo(context, R.string.screen_layout_tv_key);
        if (forcedToTv != null) {
            return forcedToTv;
        }

        final PackageManager pm = App.getApp().getPackageManager();

        // from doc: https://developer.android.com/training/tv/start/hardware.html#runtime-check
        boolean isTv = ContextCompat.getSystemService(context, UiModeManager.class)
                .getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
                || pm.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);

        // from https://stackoverflow.com/a/58932366
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final boolean isBatteryAbsent = context.getSystemService(BatteryManager.class)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 0;
            isTv = isTv || (isBatteryAbsent
                    && !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                    && pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
                    && pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isTv = isTv || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        }

        DeviceUtils.isTV = isTv;
        return DeviceUtils.isTV;
    }

    public static boolean isTablet(@NonNull final Context context) {
        final Boolean forcedToTablet = layoutForcedTo(context, R.string.screen_layout_tablet_key);
        if (forcedToTablet != null) {
            return forcedToTablet;
        }

        return (context
                .getResources()
                .getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
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
