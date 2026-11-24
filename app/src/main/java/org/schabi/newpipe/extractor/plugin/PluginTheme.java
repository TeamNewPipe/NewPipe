/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import com.grack.nanojson.JsonObject;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional theme colors declared by a plugin in {@code plugin.json}.
 *
 * <p>The host applies these colors as a theme overlay on top of its normal light, dark or black
 * theme. A plugin therefore only has to describe colors, not layouts, styles or drawables.</p>
 *
 * <pre>
 * "theme": {
 *   "light": "#F57C00",
 *   "dark":  "#A35300"
 * }
 * </pre>
 *
 * <p>Only the primary color of the light and the dark theme can be declared, as {@code #RRGGBB}.
 * One of them may be omitted and is then taken from the other. The black theme uses the dark
 * color. The status bar uses the primary color; the accent color is black in the light theme and
 * white in the dark theme and cannot be configured.</p>
 */
public final class PluginTheme {

    /** A color set for one host theme variant. */
    public static final class Variant {
        private final int primary;
        private final int accent;

        private Variant(final int primary, final boolean lightTheme) {
            this.primary = primary;
            this.accent = lightTheme ? 0xFF000000 : 0xFFFFFFFF;
        }

        public int getPrimary() {
            return primary;
        }

        public int getAccent() {
            return accent;
        }
    }

    @Nonnull
    private final Variant light;
    @Nonnull
    private final Variant dark;
    @Nonnull
    private final Variant black;

    private PluginTheme(@Nonnull final Variant light, @Nonnull final Variant dark,
                        @Nonnull final Variant black) {
        this.light = light;
        this.dark = dark;
        this.black = black;
    }

    /**
     * @param darkMode  whether the host is using its dark theme
     * @param blackMode whether the host is using its black theme
     * @return the colors matching the host theme
     */
    @Nonnull
    public Variant getVariant(final boolean darkMode, final boolean blackMode) {
        if (blackMode) {
            return black;
        }
        return darkMode ? dark : light;
    }

    @Nonnull
    public Variant getLight() {
        return light;
    }

    @Nonnull
    public Variant getDark() {
        return dark;
    }

    @Nonnull
    public Variant getBlack() {
        return black;
    }

    @Nullable
    static PluginTheme parse(@Nullable final JsonObject o) throws PluginException {
        if (o == null) {
            return null;
        }
        final boolean hasLight = o.has("light");
        final boolean hasDark = o.has("dark");
        if (!hasLight && !hasDark) {
            throw new PluginException("theme needs a light or dark color");
        }
        final int lightColor = hasLight ? parseColor(o.getString("light", null), "light") : 0;
        final int darkColor = hasDark ? parseColor(o.getString("dark", null), "dark") : 0;
        final Variant light = new Variant(hasLight ? lightColor : darkColor, true);
        final Variant dark = new Variant(hasDark ? darkColor : lightColor, false);
        return new PluginTheme(light, dark, dark);
    }

    private static int parseColor(@Nullable final String value, final String name)
            throws PluginException {
        if (value == null) {
            throw new PluginException("Missing theme color " + name);
        }
        final String hex = value.trim();
        if (!hex.matches("^#[0-9a-fA-F]{6}$")) {
            throw new PluginException("Invalid theme color " + name + ": " + value);
        }
        return (int) Long.parseLong(hex.substring(1).toLowerCase(Locale.ROOT), 16) | 0xFF000000;
    }
}
