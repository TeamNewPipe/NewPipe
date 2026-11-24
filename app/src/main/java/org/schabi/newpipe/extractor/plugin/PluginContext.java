/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import javax.annotation.Nonnull;

/**
 * Host facilities exposed to a plugin. Network access goes through
 * {@link org.schabi.newpipe.extractor.NewPipe#getDownloader()} as for built-in services.
 */
public interface PluginContext {

    /**
     * @return the plugin id declared in {@code plugin.json}
     */
    @Nonnull
    String getPluginId();

    /**
     * @return key-value storage private to this plugin. Values of the settings declared in
     * {@code plugin.json} are stored here under their {@code key}.
     */
    @Nonnull
    PluginPreferences getPreferences();
}
