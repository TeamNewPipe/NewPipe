/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import javax.annotation.Nonnull;

/**
 * Cookie based web login declared by a plugin in {@code plugin.json}. The host opens
 * {@link #getUrl()} in a web view and, once the cookie named {@link #getCookie()} is set,
 * stores the whole cookie header of the page in the plugin's {@link PluginPreferences} under
 * {@link #getKey()}. Logging out stores an empty string. Plugins read the value and observe
 * changes through {@link PluginPreferences#addChangeListener}.
 */
public final class PluginLogin {

    /** Preference key used when {@code login.key} is omitted. */
    public static final String DEFAULT_KEY = "login_cookies";

    @Nonnull
    private final String url;
    @Nonnull
    private final String cookie;
    @Nonnull
    private final String key;

    public PluginLogin(@Nonnull final String url,
                       @Nonnull final String cookie,
                       @Nonnull final String key) {
        this.url = url;
        this.cookie = cookie;
        this.key = key;
    }

    /**
     * @return the https page the web view opens
     */
    @Nonnull
    public String getUrl() {
        return url;
    }

    /**
     * @return the name of the cookie whose presence means the login succeeded
     */
    @Nonnull
    public String getCookie() {
        return cookie;
    }

    /**
     * @return the preference key the cookie header is stored under
     */
    @Nonnull
    public String getKey() {
        return key;
    }
}
