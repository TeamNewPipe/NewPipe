package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.schabi.newpipe.R;
import org.schabi.newpipe.themes.CustomAccentColors;
import org.schabi.newpipe.util.ThemeHelper;

/** Owns the seek-bar palette for a video UI, including service-hosted popup playback. */
public final class PlayerUiTheme implements AutoCloseable {
    private final Context context;
    private final SeekBar seekBar;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            this::onThemeChanged;
    private boolean closed;

    public PlayerUiTheme(@NonNull final Context context, @NonNull final SeekBar seekBar) {
        this.context = context;
        this.seekBar = seekBar;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    /**
     * Build a fresh player palette without changing the service's theme or retaining an activity.
     * @param context the player service context
     * @return a context suitable for inflating player controls
     */
    @NonNull
    public static Context createContext(@NonNull final Context context) {
        final boolean light = ThemeHelper.isLightThemeSelected(context);
        final Configuration configuration = new Configuration(context.getResources()
                .getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (light ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        Context themed = new ContextThemeWrapper(context.createConfigurationContext(configuration),
                ThemeHelper.getThemeForService(context, -1));
        final Integer customColor = ThemeHelper.getCustomThemeColor(context);
        if (customColor != null && !ThemeHelper.isEyeComfortSelected(context)) {
            themed = CustomAccentColors.wrap(themed, customColor, light,
                    ThemeHelper.isBlackThemeSelected(context));
        } else if (ThemeHelper.isFollowSystemThemeColor(context)) {
            final int overlay = light
                    ? com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
                    : com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark;
            themed = DynamicColors.wrapContextIfAvailable(themed, overlay);
            if (ThemeHelper.isBlackThemeSelected(context)) {
                themed.getTheme().applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true);
            }
        } else {
            ThemeHelper.applyThemeColorOverlay(themed);
        }
        org.schabi.newpipe.util.AppFonts.prepareContext(themed);
        return themed;
    }

    public void refresh() {
        if (!closed) {
            // A service and its existing views can outlive both the activity and its palette.
            applyColors(seekBar, createContext(context));
        }
    }

    static void applyColors(final SeekBar seekBar, final Context palette) {
        final ColorStateList active = color(palette, R.attr.colorPrimary);
        seekBar.setProgressTintList(active);
        seekBar.setProgressTintMode(PorterDuff.Mode.SRC_IN);
        seekBar.setThumbTintList(active);
        seekBar.setThumbTintMode(PorterDuff.Mode.SRC_IN);
        final int[] tracks = trackColors(active.getDefaultColor(),
                ThemeHelper.resolveColorFromAttr(palette,
                        com.google.android.material.R.attr.colorSurfaceVariant));
        seekBar.setSecondaryProgressTintList(ColorStateList.valueOf(tracks[0]));
        seekBar.setSecondaryProgressTintMode(PorterDuff.Mode.SRC_IN);
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(tracks[1]));
        seekBar.setProgressBackgroundTintMode(PorterDuff.Mode.SRC_IN);
    }

    /**
     * Keep the theme accent for played video and separate the other two ranges by luminance.
     * Container and surface roles can be almost identical, especially in dynamic palettes.
     * @param accent played-range theme accent
     * @param surface preferred unplayed-range theme surface
     * @return buffered and unplayed colors, both opaque so video content cannot erase contrast
     */
    static int[] trackColors(final int accent, final int surface) {
        final int active = ColorUtils.setAlphaComponent(accent, 255);
        int remaining = ColorUtils.setAlphaComponent(surface, 255);
        final int endpoint = ColorUtils.calculateLuminance(active) > 0.179
                ? Color.BLACK : Color.WHITE;
        for (int step = 0; step <= 100; step++) {
            remaining = ColorUtils.blendARGB(surface, endpoint, step / 100f);
            remaining = ColorUtils.setAlphaComponent(remaining, 255);
            if (ColorUtils.calculateContrast(active, remaining) >= 4.5) {
                break;
            }
        }
        // Equal contrast ratios on each side are achieved at the geometric midpoint of
        // relative luminance + 0.05, not at the midpoint of the RGB components.
        final double target = Math.sqrt((ColorUtils.calculateLuminance(active) + 0.05)
                * (ColorUtils.calculateLuminance(remaining) + 0.05)) - 0.05;
        int buffered = active;
        double closest = Double.MAX_VALUE;
        for (int step = 0; step <= 100; step++) {
            final int candidate = ColorUtils.blendARGB(active, remaining, step / 100f);
            final double distance = Math.abs(ColorUtils.calculateLuminance(candidate) - target);
            if (distance < closest) {
                closest = distance;
                buffered = candidate;
            }
        }
        return new int[]{buffered, remaining};
    }

    private static ColorStateList color(final Context palette, final int attribute) {
        return ColorStateList.valueOf(ThemeHelper.resolveColorFromAttr(palette, attribute));
    }

    private void onThemeChanged(final SharedPreferences unused, final String key) {
        if (key == null || key.equals(org.schabi.newpipe.themes.ThemePacks.KEY)
                || key.equals(context.getString(R.string.theme_key))
                || key.equals(context.getString(R.string.night_theme_key))
                || key.equals(context.getString(R.string.theme_color_key))
                || key.equals(context.getString(R.string.custom_theme_color_key))) {
            handler.removeCallbacksAndMessages(null);
            handler.post(this::refresh);
        }
    }

    @Override
    public void close() {
        closed = true;
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        handler.removeCallbacksAndMessages(null);
    }
}
