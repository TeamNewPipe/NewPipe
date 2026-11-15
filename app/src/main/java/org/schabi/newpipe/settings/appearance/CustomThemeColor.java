package org.schabi.newpipe.settings.appearance;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class CustomThemeColor {
    private CustomThemeColor() { }

    @Nullable
    public static String normalize(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("#[0-9A-F]{6}")) {
            return null;
        }
        return normalized;
    }

    @ColorInt
    public static int parse(@NonNull final String normalizedValue) {
        return Color.parseColor(normalizedValue);
    }
}
