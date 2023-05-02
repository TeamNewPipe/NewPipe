package org.schabi.newpipe.util.image;

import android.content.Context;

import org.schabi.newpipe.R;

public enum PreferredImageQuality {
    NONE,
    LOW,
    MEDIUM,
    HIGH;

    public static PreferredImageQuality fromPreferenceKey(final Context context, final String key) {
        if (context.getString(R.string.image_quality_none_key).equals(key)) {
            return NONE;
        } else if (context.getString(R.string.image_quality_low_key).equals(key)) {
            return LOW;
        } else if (context.getString(R.string.image_quality_high_key).equals(key)) {
            return HIGH;
        } else {
            return MEDIUM; // default to medium
        }
    }
}
