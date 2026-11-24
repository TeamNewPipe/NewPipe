/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The {@code plugin.json} file at the root of a plugin package.
 *
 * <pre>
 * {
 *   "pluginId": "org.example.myservice",     // reverse domain, [a-z0-9_.]
 *   "name": "MyService",                      // must equal StreamingService name
 *   "versionCode": 3,
 *   "versionName": "1.2.0",
 *   "apiVersion": "1.0",                      // major.minor of PipePipePlugin API
 *   "author": "Jane Doe",                     // shown as "by Jane Doe" in the plugin settings
 *   "entryClass": "org.example.myservice.MyServicePlugin",
 *   "legacyServiceId": 1,                     // optional, only for ex built-in services
 *   "issueUrl": "https://example.org/issues", // optional, where users report bugs of this plugin
 *   "description": "...",                     // optional
 *   "icon": "icon.png",                       // optional, path of a PNG/WebP in the package
 *   "settings": [                             // optional
 *     {"key": "instance", "type": "string", "title": "Instance", "default": "https://..."}
 *   ],
 *   "login": {                                // optional, cookie based web login
 *     "url": "https://example.org/login",     // https page opened in a web view
 *     "cookie": "session",                    // cookie whose presence means logged in
 *     "key": "login_cookies"                  // optional, preference key of the cookie header
 *   },
 *   "theme": {                                // optional, primary colors as #RRGGBB
 *     "light": "#F57C00",
 *     "dark": "#A35300"
 *   }
 * }
 * </pre>
 *
 * <p>The icon should be a square, single color image on a transparent background (at most
 * {@value #ICON_MAX_DIMENSION} px per side): hosts tint it like other icons.</p>
 */
public final class PluginManifest {

    public static final String FILE_NAME = "plugin.json";
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$");
    private static final Pattern API_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");
    private static final Pattern ICON_PATTERN =
            Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_./-]{0,127}$");

    /** Largest width or height, in pixels, hosts accept for {@code icon}. */
    public static final int ICON_MAX_DIMENSION = 512;

    /** Longest url hosts accept for {@code issueUrl}. */
    private static final int URL_MAX_LENGTH = 512;

    @Nonnull
    private final String pluginId;
    @Nonnull
    private final String name;
    private final int versionCode;
    @Nonnull
    private final String versionName;
    private final int apiMajor;
    private final int apiMinor;
    @Nonnull
    private final String host;
    private final int minHostVersionCode;
    private final int minAndroidSdk;
    @Nonnull
    private final List<String> requiredCapabilities;
    @Nonnull
    private final String author;
    @Nonnull
    private final String entryClass;
    private final int legacyServiceId;
    @Nullable
    private final String issueUrl;
    @Nullable
    private final String description;
    @Nullable
    private final String icon;
    @Nonnull
    private final List<PluginSetting> settings;
    @Nullable
    private final PluginLogin login;
    @Nullable
    private final PluginTheme theme;

    private PluginManifest(@Nonnull final String pluginId,
                           @Nonnull final String name,
                           final int versionCode,
                           @Nonnull final String versionName,
                           final int apiMajor,
                           final int apiMinor,
                           @Nonnull final String host,
                           final int minHostVersionCode,
                           final int minAndroidSdk,
                           @Nonnull final List<String> requiredCapabilities,
                           @Nonnull final String author,
                           @Nonnull final String entryClass,
                           final int legacyServiceId,
                           @Nullable final String issueUrl,
                           @Nullable final String description,
                           @Nullable final String icon,
                           @Nonnull final List<PluginSetting> settings,
                           @Nullable final PluginLogin login,
                           @Nullable final PluginTheme theme) {
        this.pluginId = pluginId;
        this.name = name;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apiMajor = apiMajor;
        this.apiMinor = apiMinor;
        this.host = host;
        this.minHostVersionCode = minHostVersionCode;
        this.minAndroidSdk = minAndroidSdk;
        this.requiredCapabilities = requiredCapabilities;
        this.author = author;
        this.entryClass = entryClass;
        this.legacyServiceId = legacyServiceId;
        this.issueUrl = issueUrl;
        this.description = description;
        this.icon = icon;
        this.settings = settings;
        this.login = login;
        this.theme = theme;
    }

    @Nonnull
    public static PluginManifest parse(@Nonnull final String json) throws PluginException {
        final JsonObject o;
        try {
            o = JsonParser.object().from(json);
        } catch (final JsonParserException e) {
            throw new PluginException("Invalid " + FILE_NAME, e);
        }

        final String pluginId = requireString(o, "pluginId");
        if (!isValidPluginId(pluginId)) {
            throw new PluginException("Invalid pluginId: " + pluginId);
        }
        final String apiVersion = requireString(o, "apiVersion");
        final java.util.regex.Matcher m = API_PATTERN.matcher(apiVersion);
        if (!m.matches()) {
            throw new PluginException("Invalid apiVersion: " + apiVersion);
        }
        if (!o.has("versionCode") || !(o.get("versionCode") instanceof Number)) {
            throw new PluginException("Missing versionCode");
        }
        final int versionCode = WizeStreamPluginApi.readNonNegativeInt(o, "versionCode", 0);
        final int major;
        final int minor;
        try {
            major = Integer.parseInt(m.group(1));
            minor = Integer.parseInt(m.group(2));
        } catch (final NumberFormatException e) {
            throw new PluginException("Invalid apiVersion: " + apiVersion, e);
        }

        final String icon = o.getString("icon", null);
        if (icon != null && (!ICON_PATTERN.matcher(icon).matches() || icon.contains(".."))) {
            throw new PluginException("Invalid icon path: " + icon);
        }

        final String issueUrl = o.getString("issueUrl", null);
        if (issueUrl != null
                && (!issueUrl.startsWith("https://") || issueUrl.length() > URL_MAX_LENGTH)) {
            throw new PluginException("issueUrl must be https");
        }

        final List<PluginSetting> settings = new ArrayList<>();
        final JsonArray settingsArray = o.getArray("settings");
        for (int i = 0; i < settingsArray.size(); i++) {
            final JsonObject s = settingsArray.getObject(i);
            final String key = requireString(s, "key");
            final PluginSetting.Type type;
            try {
                type = PluginSetting.Type.valueOf(
                        requireString(s, "type").toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                throw new PluginException("Unknown setting type for " + key, e);
            }
            final Object def = s.get("default");
            settings.add(new PluginSetting(key, type, requireString(s, "title"),
                    s.getString("summary", null), def == null ? null : String.valueOf(def)));
        }

        PluginLogin login = null;
        if (o.has("login")) {
            final JsonObject l = o.getObject("login");
            final String url = requireString(l, "url");
            if (!url.startsWith("https://")) {
                throw new PluginException("login.url must be https");
            }
            final String key = l.getString("key", PluginLogin.DEFAULT_KEY);
            if (key.trim().isEmpty()) {
                throw new PluginException("Invalid login.key");
            }
            login = new PluginLogin(url, requireString(l, "cookie"), key);
        }

        final PluginTheme theme = o.has("theme") ? PluginTheme.parse(o.getObject("theme")) : null;

        return new PluginManifest(
                pluginId,
                requireString(o, "name"),
                versionCode,
                o.getString("versionName", String.valueOf(versionCode)),
                major,
                minor,
                WizeStreamPluginApi.readHost(o),
                WizeStreamPluginApi.readMinHostVersionCode(o),
                WizeStreamPluginApi.readMinAndroidSdk(o),
                WizeStreamPluginApi.readRequiredCapabilities(o),
                requireString(o, "author").trim(),
                requireString(o, "entryClass"),
                o.getInt("legacyServiceId", -1),
                issueUrl,
                o.getString("description", null),
                icon,
                Collections.unmodifiableList(settings),
                login,
                theme);
    }

    public static boolean isValidPluginId(@Nullable final String pluginId) {
        return pluginId != null && pluginId.length() <= 128
                && ID_PATTERN.matcher(pluginId).matches();
    }

    /**
     * @return whether this host can load a plugin built against the given API version
     */
    public static boolean isApiCompatible(final int major, final int minor) {
        return WizeStreamPluginApi.isApiCompatible(WizeStreamPluginApi.PIPEPIPE_HOST, major, minor);
    }

    public boolean isApiCompatible() {
        return WizeStreamPluginApi.isApiCompatible(host, apiMajor, apiMinor);
    }

    @Nonnull
    public String getHost() {
        return host;
    }

    public int getMinHostVersionCode() {
        return minHostVersionCode;
    }

    public int getMinAndroidSdk() {
        return minAndroidSdk;
    }

    @Nonnull
    public List<String> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    @Nonnull
    private static String requireString(final JsonObject o, final String key)
            throws PluginException {
        final String value = o.getString(key, null);
        if (value == null || value.trim().isEmpty()) {
            throw new PluginException("Missing " + key);
        }
        return value;
    }

    @Nonnull
    public String getPluginId() {
        return pluginId;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public int getVersionCode() {
        return versionCode;
    }

    @Nonnull
    public String getVersionName() {
        return versionName;
    }

    public int getApiMajor() {
        return apiMajor;
    }

    public int getApiMinor() {
        return apiMinor;
    }

    @Nonnull
    public String getApiVersion() {
        return apiMajor + "." + apiMinor;
    }

    /**
     * @return the name of the plugin's author, hosts show it as "by author"
     */
    @Nonnull
    public String getAuthor() {
        return author;
    }

    @Nonnull
    public String getEntryClass() {
        return entryClass;
    }

    /**
     * @return the id this service had while it was built into PipePipe, or -1. Hosts reuse
     * it so that existing subscriptions and history keep working.
     */
    public int getLegacyServiceId() {
        return legacyServiceId;
    }

    /**
     * @return where bugs of this plugin are reported, or null if it declares no tracker.
     * Hosts send users there instead of their own tracker when a failure comes from this
     * plugin's code.
     */
    @Nullable
    public String getIssueUrl() {
        return issueUrl;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * @return the path of the icon inside the package, or null if the plugin has none
     */
    @Nullable
    public String getIcon() {
        return icon;
    }

    @Nonnull
    public List<PluginSetting> getSettings() {
        return settings;
    }

    /**
     * @return the web login of the plugin, or null if it has none
     */
    @Nullable
    public PluginLogin getLogin() {
        return login;
    }

    /**
     * @return the theme colors declared by the plugin, or null if it declares none
     */
    @Nullable
    public PluginTheme getTheme() {
        return theme;
    }
}
