package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

public final class PrivacyHelper {
    private PrivacyHelper() {
    }

    public static void setScreenshotMode(final Activity activity) {
        final Context context = activity.getApplicationContext();
        final SharedPreferences sharedPreferences
                = PreferenceManager.getDefaultSharedPreferences(context);
        if (!sharedPreferences.getBoolean(context.getString(R.string.enable_screen_capture_key),
                true)) {
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
