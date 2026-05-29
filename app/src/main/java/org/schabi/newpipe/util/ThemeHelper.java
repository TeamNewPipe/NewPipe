package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.info_list.ItemViewMode;

import java.util.Locale;

public final class ThemeHelper {
    private ThemeHelper() { }

    public static void setTheme(final Context context) {
        setTheme(context, -1);
    }

    public static void setTheme(final Context context, final int serviceId) {
        context.setTheme(getThemeForService(context, serviceId));
        applyThemeColor(context);
    }

    public static void applyThemeColor(final Context context) {
        if (shouldApplyDynamicColors(context)) {
            DynamicColors.applyToActivityIfAvailable((Activity) context);
        } else {
            applyThemeColorOverlay(context);
        }
    }

    public static String getThemeColorPreference(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.theme_color_key),
                context.getString(R.string.default_theme_color_value));
    }

    public static boolean isFollowSystemThemeColor(final Context context) {
        return isThemeColor(context, R.string.theme_color_follow_system_value, "follow_system");
    }

    public static boolean shouldApplyDynamicColors(final Context context) {
        return context instanceof Activity
                && isFollowSystemThemeColor(context)
                && !isBlackThemeSelected(context);
    }

    public static void applyThemeColorOverlay(final Context context) {
        final int overlay = getThemeColorOverlay(context);
        if (overlay != 0) {
            context.getTheme().applyStyle(overlay, true);
        }
    }

    @StyleRes
    private static int getThemeColorOverlay(final Context context) {
        if (isThemeColor(context, R.string.theme_color_newpipe_material_value,
                "newpipe_material")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_NewPipeMaterial;
        } else if (isThemeColor(context, R.string.theme_color_neutral_value, "neutral")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Neutral;
        } else if (isThemeColor(context, R.string.theme_color_green_value, "green")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Green;
        } else if (isThemeColor(context, R.string.theme_color_blue_value, "blue")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Blue;
        } else if (isThemeColor(context, R.string.theme_color_purple_value, "purple")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Purple;
        } else if (isThemeColor(context, R.string.theme_color_orange_value, "orange")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Orange;
        } else if (isThemeColor(context, R.string.theme_color_pink_value, "pink")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Pink;
        } else if (isThemeColor(context, R.string.theme_color_red_value, "red")) {
            return R.style.ThemeOverlay_NewPipeMaterial_ThemeColor_Red;
        }
        return 0;
    }

    public static boolean isLightThemeSelected(final Context context) {
        final String selectedThemeKey = getSelectedThemeKey(context);
        final Resources res = context.getResources();
        return selectedThemeKey.equals(res.getString(R.string.light_theme_key))
                || (selectedThemeKey.equals(res.getString(R.string.auto_device_theme_key))
                && !isDeviceDarkThemeEnabled(context));
    }

    public static boolean isBlackThemeSelected(final Context context) {
        final String selectedThemeKey = getSelectedThemeKey(context);
        final Resources res = context.getResources();
        final String blackThemeKey = res.getString(R.string.black_theme_key);
        return selectedThemeKey.equals(blackThemeKey)
                || (selectedThemeKey.equals(res.getString(R.string.auto_device_theme_key))
                && isDeviceDarkThemeEnabled(context)
                && getSelectedNightThemeKey(context).equals(blackThemeKey));
    }

    @StyleRes
    public static int getDialogTheme(final Context context) {
        return getDialogTheme(context, false);
    }

    @StyleRes
    public static int getMinWidthDialogTheme(final Context context) {
        return getDialogTheme(context, true);
    }

    @StyleRes
    private static int getDialogTheme(final Context context, final boolean minWidth) {
        final boolean light = isLightThemeSelected(context);
        final int defaultTheme = minWidth
                ? (light ? R.style.LightDialogMinWidthTheme : R.style.DarkDialogMinWidthTheme)
                : (light ? R.style.LightDialogTheme : R.style.DarkDialogTheme);
        if (isFollowSystemThemeColor(context)) {
            return defaultTheme;
        }
        if (isThemeColor(context, R.string.theme_color_newpipe_material_value,
                "newpipe_material")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_NewPipeMaterial
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_NewPipeMaterial)
                    : (light ? R.style.LightDialogTheme_ThemeColor_NewPipeMaterial
                            : R.style.DarkDialogTheme_ThemeColor_NewPipeMaterial);
        } else if (isThemeColor(context, R.string.theme_color_neutral_value, "neutral")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Neutral
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Neutral)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Neutral
                            : R.style.DarkDialogTheme_ThemeColor_Neutral);
        } else if (isThemeColor(context, R.string.theme_color_green_value, "green")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Green
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Green)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Green
                            : R.style.DarkDialogTheme_ThemeColor_Green);
        } else if (isThemeColor(context, R.string.theme_color_blue_value, "blue")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Blue
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Blue)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Blue
                            : R.style.DarkDialogTheme_ThemeColor_Blue);
        } else if (isThemeColor(context, R.string.theme_color_purple_value, "purple")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Purple
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Purple)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Purple
                            : R.style.DarkDialogTheme_ThemeColor_Purple);
        } else if (isThemeColor(context, R.string.theme_color_orange_value, "orange")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Orange
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Orange)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Orange
                            : R.style.DarkDialogTheme_ThemeColor_Orange);
        } else if (isThemeColor(context, R.string.theme_color_pink_value, "pink")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Pink
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Pink)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Pink
                            : R.style.DarkDialogTheme_ThemeColor_Pink);
        } else if (isThemeColor(context, R.string.theme_color_red_value, "red")) {
            return minWidth
                    ? (light ? R.style.LightDialogMinWidthTheme_ThemeColor_Red
                            : R.style.DarkDialogMinWidthTheme_ThemeColor_Red)
                    : (light ? R.style.LightDialogTheme_ThemeColor_Red
                            : R.style.DarkDialogTheme_ThemeColor_Red);
        }
        return defaultTheme;
    }

    private static boolean isThemeColor(final Context context, final int stringRes,
                                        final String fallbackValue) {
        final String preferenceValue = normalizeThemeColorValue(getThemeColorPreference(context));
        return preferenceValue.equals(normalizeThemeColorValue(context.getString(stringRes)))
                || preferenceValue.equals(normalizeThemeColorValue(fallbackValue));
    }

    private static String normalizeThemeColorValue(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @StyleRes
    public static int getThemeForService(final Context context, final int serviceId) {
        final Resources res = context.getResources();
        final String lightThemeKey = res.getString(R.string.light_theme_key);
        final String blackThemeKey = res.getString(R.string.black_theme_key);
        final String automaticDeviceThemeKey = res.getString(R.string.auto_device_theme_key);
        final String selectedThemeKey = getSelectedThemeKey(context);
        int baseTheme = R.style.DarkTheme;
        if (selectedThemeKey.equals(lightThemeKey)) {
            baseTheme = R.style.LightTheme;
        } else if (selectedThemeKey.equals(blackThemeKey)) {
            baseTheme = R.style.BlackTheme;
        } else if (selectedThemeKey.equals(automaticDeviceThemeKey)) {
            if (isDeviceDarkThemeEnabled(context)) {
                final String selectedNightThemeKey = getSelectedNightThemeKey(context);
                baseTheme = selectedNightThemeKey.equals(blackThemeKey)
                        ? R.style.BlackTheme : R.style.DarkTheme;
            } else {
                baseTheme = R.style.LightTheme;
            }
        }
        if (serviceId <= -1) {
            return baseTheme;
        }
        final StreamingService service;
        try {
            service = NewPipe.getService(serviceId);
        } catch (final ExtractionException ignored) {
            return baseTheme;
        }
        String themeName = "DarkTheme";
        if (baseTheme == R.style.LightTheme) {
            themeName = "LightTheme";
        } else if (baseTheme == R.style.BlackTheme) {
            themeName = "BlackTheme";
        }
        themeName += "." + service.getServiceInfo().getName();
        final int resourceId = context.getResources()
                .getIdentifier(themeName, "style", context.getPackageName());
        return resourceId > 0 ? resourceId : baseTheme;
    }

    @StyleRes
    public static int getSettingsThemeStyle(final Context context) {
        final Resources res = context.getResources();
        final String lightTheme = res.getString(R.string.light_theme_key);
        final String blackTheme = res.getString(R.string.black_theme_key);
        final String automaticDeviceTheme = res.getString(R.string.auto_device_theme_key);
        final String selectedTheme = getSelectedThemeKey(context);
        if (selectedTheme.equals(lightTheme)) {
            return R.style.LightSettingsTheme;
        } else if (selectedTheme.equals(blackTheme)) {
            return R.style.BlackSettingsTheme;
        } else if (selectedTheme.equals(automaticDeviceTheme)) {
            if (isDeviceDarkThemeEnabled(context)) {
                final String selectedNightTheme = getSelectedNightThemeKey(context);
                return selectedNightTheme.equals(blackTheme)
                        ? R.style.BlackSettingsTheme : R.style.DarkSettingsTheme;
            } else {
                return R.style.LightSettingsTheme;
            }
        } else {
            return R.style.DarkSettingsTheme;
        }
    }

    public static int resolveColorFromAttr(final Context context, @AttrRes final int attrColor) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrColor, value, true);
        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return value.data;
    }

    public static Drawable resolveDrawable(@NonNull final Context context,
                                           @AttrRes final int attrResId) {
        final TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return AppCompatResources.getDrawable(context, typedValue.resourceId);
    }

    public static int getAndroidDimenPx(@NonNull final Context context, final String name) {
        final int resId = context.getResources().getIdentifier(name, "dimen", "android");
        if (resId <= 0) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(resId);
    }

    private static String getSelectedThemeKey(final Context context) {
        final String themeKey = context.getString(R.string.theme_key);
        final String defaultTheme = context.getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, defaultTheme);
    }

    private static String getSelectedNightThemeKey(final Context context) {
        final String nightThemeKey = context.getString(R.string.night_theme_key);
        final String defaultNightTheme = context.getResources()
                .getString(R.string.default_night_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(nightThemeKey, defaultNightTheme);
    }

    public static void setTitleToAppCompatActivity(@Nullable final Activity activity,
                                                   final CharSequence title) {
        if (activity instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        }
    }

    public static boolean isDeviceDarkThemeEnabled(final Context context) {
        final int deviceTheme = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        switch (deviceTheme) {
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            case Configuration.UI_MODE_NIGHT_NO:
            default:
                return false;
        }
    }

    public static void setDayNightMode(final Context context) {
        setDayNightMode(context, ThemeHelper.getSelectedThemeKey(context));
    }

    public static void setDayNightMode(final Context context, final String selectedThemeKey) {
        final Resources res = context.getResources();
        if (selectedThemeKey.equals(res.getString(R.string.light_theme_key))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (selectedThemeKey.equals(res.getString(R.string.dark_theme_key))
                || selectedThemeKey.equals(res.getString(R.string.black_theme_key))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static boolean shouldUseGridLayout(final Context context) {
        final ItemViewMode mode = getItemViewMode(context);
        return mode == ItemViewMode.GRID;
    }

    public static int getGridSpanCountChannels(final Context context) {
        return getGridSpanCount(context,
                context.getResources().getDimensionPixelSize(R.dimen.channel_item_grid_min_width));
    }

    public static ItemViewMode getItemViewMode(final Context context) {
        final String listMode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.list_view_mode_key),
                        context.getString(R.string.list_view_mode_value));
        final ItemViewMode result;
        if (listMode.equals(context.getString(R.string.list_view_mode_list_key))) {
            result = ItemViewMode.LIST;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_grid_key))) {
            result = ItemViewMode.GRID;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_card_key))) {
            result = ItemViewMode.CARD;
        } else {
            final Configuration configuration = context.getResources().getConfiguration();
            final boolean useGrid = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE);
            result = useGrid ? ItemViewMode.GRID : ItemViewMode.LIST;
        }
        return result;
    }

    public static int getGridSpanCountStreams(final Context context) {
        final Resources res = context.getResources();
        return getGridSpanCount(context,
                res.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
                        + res.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2);
    }

    public static int getGridSpanCount(final Context context, final int minWidth) {
        return Math.max(1, context.getResources().getDisplayMetrics().widthPixels / minWidth);
    }
}
