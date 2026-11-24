/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import org.schabi.newpipe.extractor.StreamingService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import javax.annotation.Nonnull;

/**
 * Turns a plugin package into a {@link StreamingService}. The class loader is platform
 * specific: {@link #createJvmClassLoader(File)} on a JVM, {@code DexClassLoader} on Android.
 *
 * <p>This class does not verify signatures and does not register the service; hosts call
 * {@link PluginPackage#verify} before and
 * {@link org.schabi.newpipe.extractor.ServiceList#register(StreamingService)} after.</p>
 */
public final class PluginLoader {

    private PluginLoader() {
    }

    /**
     * Class loader for JVM hosts (for example a server wrapping the extractor). Host classes
     * are resolved from the class loader of the extractor.
     */
    @Nonnull
    public static ClassLoader createJvmClassLoader(@Nonnull final File packageFile)
            throws PluginException {
        try {
            return new URLClassLoader(new URL[]{packageFile.toURI().toURL()},
                    PluginLoader.class.getClassLoader());
        } catch (final MalformedURLException e) {
            throw new PluginException("Invalid plugin path", e);
        }
    }

    /**
     * Instantiate the plugin entry class and create its service.
     *
     * @param classLoader a class loader over the plugin package
     * @param manifest    the manifest read from the same package
     * @param serviceId   the id assigned by the host
     */
    @Nonnull
    public static StreamingService createService(@Nonnull final ClassLoader classLoader,
                                                 @Nonnull final PluginManifest manifest,
                                                 final int serviceId,
                                                 @Nonnull final PluginContext context)
            throws PluginException {
        if (!manifest.isApiCompatible()) {
            throw new PluginException("Plugin " + manifest.getPluginId() + " requires "
                    + manifest.getHost() + " API " + manifest.getApiVersion());
        }

        final boolean nativePlugin = WizeStreamPluginApi.HOST.equals(manifest.getHost());
        if (nativePlugin) {
            if (!(context instanceof WizeStreamPluginContext)) {
                throw new PluginException("Native plugin requires a WizeStream host context");
            }
            WizeStreamPluginApi.requireCompatible(manifest,
                    ((WizeStreamPluginContext) context).getHostVersionCode());
        }

        final StreamingService service;
        try {
            final Class<?> clazz = Class.forName(manifest.getEntryClass(), false, classLoader);
            if (nativePlugin) {
                if (!WizeStreamPlugin.class.isAssignableFrom(clazz)) {
                    throw new PluginException(manifest.getEntryClass()
                            + " does not implement " + WizeStreamPlugin.class.getName());
                }
                final WizeStreamPlugin plugin =
                        (WizeStreamPlugin) clazz.getDeclaredConstructor().newInstance();
                service = plugin.createService(serviceId, (WizeStreamPluginContext) context);
            } else {
                if (!PipePipePlugin.class.isAssignableFrom(clazz)) {
                    throw new PluginException(manifest.getEntryClass()
                            + " does not implement " + PipePipePlugin.class.getName());
                }
                final PipePipePlugin plugin =
                        (PipePipePlugin) clazz.getDeclaredConstructor().newInstance();
                service = plugin.createService(serviceId, context);
            }
        } catch (final PluginException e) {
            throw e;
        } catch (final Throwable e) {
            // plugins are untrusted code, also catch linkage errors
            throw new PluginException("Cannot load plugin " + manifest.getPluginId(), e);
        }

        //noinspection ConstantConditions
        if (service == null) {
            throw new PluginException("Plugin " + manifest.getPluginId() + " returned no service");
        }
        if (service.getServiceId() != serviceId) {
            throw new PluginException("Plugin " + manifest.getPluginId()
                    + " ignored the assigned service id");
        }
        if (!service.getServiceInfo().getName().equals(manifest.getName())) {
            throw new PluginException("Plugin " + manifest.getPluginId()
                    + " service name does not match plugin.json");
        }
        return service;
    }
}
