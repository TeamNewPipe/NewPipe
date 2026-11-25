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
import org.schabi.newpipe.themes.CustomAccentColors;
import org.schabi.newpipe.themes.ThemePacks;

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
        AppFonts.prepareContext(context);
        if (isEyeComfortSelected(context)) {
            context.getTheme().applyStyle(R.style.EyeComfortTheme, true);
            return;
        }
        final Integer customColor = getCustomThemeColor(context);
        if (customColor != null) {
            CustomAccentColors.apply(context, customColor);
        } else if (shouldApplyDynamicColors(context)) {
            DynamicColors.applyToActivityIfAvailable((Activity) context);
            applyBlackSurfaceOverlay(context);
        } else {
            applyThemeColorOverlay(context);
        }
    }

    public static String getThemeColorPreference(final Context context) {
        final String packValue = ThemePacks.accent(context);
        if (packValue != null) {
            return packValue;
        }
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getString(R.string.theme_color_key),
                context.getString(R.string.default_theme_color_value));
    }

    public static boolean isFollowSystemThemeColor(final Context context) {
        return !isEyeComfortSelected(context)
                && isThemeColor(context, R.string.theme_color_follow_system_value, "follow_system");
    }

    public static boolean shouldApplyDynamicColors(final Context context) {
        return !isEyeComfortSelected(context) && context instanceof Activity
                && (isFollowSystemThemeColor(context) || getCustomThemeColor(context) != null);
    }

    @Nullable
    public static Integer getCustomThemeColor(final Context context) {
        return CustomAccentColors.seed(context);
    }

    private static void applyBlackSurfaceOverlay(final Context context) {
        if (isBlackThemeSelected(context)) {
            context.getTheme().applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true);
        }
    }

    public static void applyThemeColorOverlay(final Context context) {
        if (isEyeComfortSelected(context)) {
            context.getTheme().applyStyle(R.style.EyeComfortTheme, true);
            return;
        }
        final int overlay = getThemeColorOverlay(getThemeColorPreference(context));
        if (overlay != 0) {
            context.getTheme().applyStyle(overlay, true);
        }
    }

    @StyleRes
    public static int getThemeColorOverlay(final String accent) {
        if ("wizestream".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_wizestream;
        } else if ("neutral".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Neutral;
        } else if ("green".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Green;
        } else if ("blue".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Blue;
        } else if ("cyan".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Cyan;
        } else if ("purple".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Purple;
        } else if ("yellow".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Yellow;
        } else if ("orange".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Orange;
        } else if ("pink".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Pink;
        } else if ("red".equals(normalizeThemeColorValue(accent))) {
            return R.style.ThemeOverlay_wizestream_ThemeColor_Red;
        }
        return 0;
    }

    public static boolean isEyeComfortSelected(final Context context) {
        return context.getString(R.string.eye_comfort_theme_key)
                .equals(getSelectedThemeKey(context));
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
        // Dialogs should inherit the already-resolved activity palette (including
        // Material You, custom theme colors, service accents, and AMOLED surfaces)
        // and only add Material 3 dialog styling. Full dialog themes replace those
        // resolved colors and are the main reason legacy-looking dialogs can diverge
        // from the screen that launched them.
        return minWidth ? R.style.ThemeOverlay_wizestream_Dialog_MinWidth
                : R.style.ThemeOverlay_wizestream_Dialog;
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
        if (isEyeComfortSelected(context)) {
            return R.style.EyeComfortTheme;
        }
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
        return getThemeOrDefault(themeName, baseTheme);
    }

    @StyleRes
    public static int getSettingsThemeStyle(final Context context) {
        if (isEyeComfortSelected(context)) {
            return R.style.EyeComfortSettingsTheme;
        }
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
        final String packValue = ThemePacks.themeKey(context);
        if (packValue != null) {
            return packValue;
        }
        final String themeKey = context.getString(R.string.theme_key);
        final String defaultTheme = context.getString(R.string.default_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, defaultTheme);
    }

    private static String getSelectedNightThemeKey(final Context context) {
        final String packValue = ThemePacks.nightKey(context);
        if (packValue != null) {
            return packValue;
        }
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
        } else if (selectedThemeKey.equals(res.getString(R.string.eye_comfort_theme_key))
                || selectedThemeKey.equals(res.getString(R.string.dark_theme_key))
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

    public static String getOrientationPreference(final Context context, final int defaultKey,
                                                   final int portraitKey, final int landscapeKey,
                                                   final String fallback) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final String value = preferences.getString(context.getString(
                landscape ? landscapeKey : portraitKey),
                context.getString(R.string.orientation_default_key));
        return context.getString(R.string.orientation_default_key).equals(value)
                ? preferences.getString(context.getString(defaultKey), fallback) : value;
    }

    public static boolean isListLayoutPreference(final Context context, final String key) {
        return java.util.Arrays.stream(new int[]{R.string.list_view_mode_key,
                R.string.grid_columns_key, R.string.list_view_mode_portrait_key,
                R.string.list_view_mode_landscape_key, R.string.grid_columns_portrait_key,
                R.string.grid_columns_landscape_key})
                .anyMatch(resource -> context.getString(resource).equals(key));
    }

    public static ItemViewMode getItemViewMode(final Context context) {
        final String listMode = getOrientationPreference(context,
                R.string.list_view_mode_key, R.string.list_view_mode_portrait_key,
                R.string.list_view_mode_landscape_key,
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
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Full-width cards waste vertical space on landscape phones. Use
                // compact video rows unless the user explicitly selected Card/Grid.
                result = configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
                        ? ItemViewMode.GRID : ItemViewMode.LIST;
            } else {
                result = ItemViewMode.CARD;
            }
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

    @StyleRes
    private static int getThemeOrDefault(final String name, @StyleRes final int baseTheme) {
        return switch (name) {
            case "LightTheme.YouTube" -> R.style.LightTheme_YouTube;
            case "DarkTheme.YouTube" -> R.style.DarkTheme_YouTube;
            case "BlackTheme.YouTube" -> R.style.BlackTheme_YouTube;
            case "LightTheme.SoundCloud" -> R.style.LightTheme_SoundCloud;
            case "DarkTheme.SoundCloud" -> R.style.DarkTheme_SoundCloud;
            case "BlackTheme.SoundCloud" -> R.style.BlackTheme_SoundCloud;
            case "LightTheme.PeerTube" -> R.style.LightTheme_PeerTube;
            case "DarkTheme.PeerTube" -> R.style.DarkTheme_PeerTube;
            case "BlackTheme.PeerTube" -> R.style.BlackTheme_PeerTube;
            case "LightTheme.media.ccc.de" -> R.style.LightTheme_media_ccc_de;
            case "DarkTheme.media.ccc.de" -> R.style.DarkTheme_media_ccc_de;
            case "BlackTheme.media.ccc.de" -> R.style.BlackTheme_media_ccc_de;
            case "LightTheme.Bandcamp" -> R.style.LightTheme_Bandcamp;
            case "DarkTheme.Bandcamp" -> R.style.DarkTheme_Bandcamp;
            case "BlackTheme.Bandcamp" -> R.style.BlackTheme_Bandcamp;
            case "LightTheme.Vimeo" -> R.style.LightTheme_Vimeo;
            case "DarkTheme.Vimeo" -> R.style.DarkTheme_Vimeo;
            case "BlackTheme.Vimeo" -> R.style.BlackTheme_Vimeo;
            default -> baseTheme;
        };
    }
}
