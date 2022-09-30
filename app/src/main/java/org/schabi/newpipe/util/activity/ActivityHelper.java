package org.schabi.newpipe.util.activity;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

public final class ActivityHelper {
    private ActivityHelper() {
    }

    public static boolean isAndroidPictureInPictureEnabled(@NonNull final Context context) {
        // The popup mode setting can't be changed if the device is running Android < 7.0 or Android
        // Go.
        final var popupMode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.popup_configuration_key),
                        context.getString(R.string.popup_mode_legacy));
        return popupMode.equals(context.getString(R.string.popup_mode_pip));
    }

    @SuppressWarnings("deprecation")
    public static void enterPictureInPictureMode(@NonNull final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.enterPictureInPictureMode();
        }
    }
}
