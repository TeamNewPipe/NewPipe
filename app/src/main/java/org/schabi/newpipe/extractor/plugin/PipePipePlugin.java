/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import org.schabi.newpipe.extractor.StreamingService;

import javax.annotation.Nonnull;

/**
 * Entry point of an external service plugin.
 *
 * <p>A plugin package declares the fully qualified name of its implementation in
 * {@code plugin.json} ({@code entryClass}). The host instantiates it through a public
 * no-arg constructor and calls {@link #createService(int, PluginContext)} once per process.</p>
 *
 * <p>Plugins must only depend on the extractor API (everything outside
 * {@code org.schabi.newpipe.extractor.services}), nanojson and jsoup, all provided by the
 * host at runtime. Bundling other libraries is allowed but they must not clash with
 * host classes.</p>
 */
public interface PipePipePlugin {

    /**
     * Major version of the plugin API implemented by this extractor build. Plugins whose
     * {@code apiVersion} major differs are rejected.
     */
    int API_VERSION_MAJOR = 1;

    /**
     * Minor version of the plugin API. Minor bumps only add API; a plugin compiled against
     * minor {@code n} can be loaded by any host with minor {@code >= n}.
     */
    int API_VERSION_MINOR = 1;

    /**
     * @param serviceId the numeric id assigned by the host. It must be passed to the
     *                  {@link StreamingService} constructor unchanged.
     * @param context   host facilities available to the plugin
     * @return the service implementation
     */
    @Nonnull
    StreamingService createService(int serviceId, @Nonnull PluginContext context);
}
