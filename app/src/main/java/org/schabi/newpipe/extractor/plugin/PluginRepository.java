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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A plugin repository index ({@code index.json}), a static file listing installable plugins.
 *
 * <pre>
 * {
 *   "formatVersion": 1,
 *   "name": "PipePipe Official",
 *   "plugins": [{
 *     "pluginId": "org.example.myservice",
 *     "name": "MyService",
 *     "versionCode": 3,
 *     "versionName": "1.2.0",
 *     "apiVersion": "1.0",
 *     "author": "Jane Doe",
 *     "package": "myservice-3.ppp",           // absolute or relative to the index url
 *     "sha256": "hex",
 *     "signature": "base64 Ed25519 signature of the raw sha256 digest",
 *     "publicKey": "base64 raw Ed25519 public key",
 *     "description": "...",                   // optional
 *     "nsfw": false                           // optional
 *   }]
 * }
 * </pre>
 */
public final class PluginRepository {

    public static final int FORMAT_VERSION = 1;
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern API_VERSION = Pattern.compile("^(\\d+)(?:\\.(\\d+))?$");

    @Nonnull
    private final String url;
    @Nonnull
    private final String name;
    @Nonnull
    private final List<Entry> plugins;

    private PluginRepository(@Nonnull final String url,
                             @Nonnull final String name,
                             @Nonnull final List<Entry> plugins) {
        this.url = url;
        this.name = name;
        this.plugins = plugins;
    }

    /**
     * Parse an index. Malformed plugin entries are skipped, so that one broken entry does
     * not hide the rest of the repository.
     *
     * @param indexUrl the url the index was downloaded from, used to resolve relative
     *                 package urls
     */
    @Nonnull
    public static PluginRepository parse(@Nonnull final String indexUrl,
                                         @Nonnull final String json) throws PluginException {
        final JsonObject o;
        try {
            o = JsonParser.object().from(json);
        } catch (final JsonParserException e) {
            throw new PluginException("Invalid repository index", e);
        }
        final int format = o.getInt("formatVersion", -1);
        if (format != FORMAT_VERSION) {
            throw new PluginException("Unsupported repository format: " + format);
        }

        final List<Entry> entries = new ArrayList<>();
        final JsonArray array = o.getArray("plugins");
        for (int i = 0; i < array.size(); i++) {
            final Entry entry = Entry.parse(indexUrl, array.getObject(i));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new PluginRepository(indexUrl, o.getString("name", indexUrl),
                Collections.unmodifiableList(entries));
    }

    @Nonnull
    public String getUrl() {
        return url;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public List<Entry> getPlugins() {
        return plugins;
    }

    public static final class Entry {
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
        @Nullable
        private final String author;
        @Nonnull
        private final String packageUrl;
        @Nonnull
        private final String sha256;
        @Nonnull
        private final String signature;
        @Nonnull
        private final String publicKey;
        @Nullable
        private final String description;
        private final boolean nsfw;

        @SuppressWarnings("checkstyle:ParameterNumber")
        private Entry(@Nonnull final String pluginId, @Nonnull final String name,
                      final int versionCode, @Nonnull final String versionName,
                      final int apiMajor, final int apiMinor,
                      @Nonnull final String host, final int minHostVersionCode,
                      final int minAndroidSdk,
                      @Nonnull final List<String> requiredCapabilities,
                      @Nullable final String author,
                      @Nonnull final String packageUrl, @Nonnull final String sha256,
                      @Nonnull final String signature, @Nonnull final String publicKey,
                      @Nullable final String description, final boolean nsfw) {
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
            this.packageUrl = packageUrl;
            this.sha256 = sha256;
            this.signature = signature;
            this.publicKey = publicKey;
            this.description = description;
            this.nsfw = nsfw;
        }

        @Nullable
        private static Entry parse(@Nonnull final String indexUrl,
                                   @Nullable final JsonObject o) {
            if (o == null) {
                return null;
            }
            final String pluginId = o.getString("pluginId", null);
            final String name = o.getString("name", null);
            final String apiVersion = o.getString("apiVersion", null);
            final String pkg = o.getString("package", null);
            final String sha256 = o.getString("sha256", null);
            final String signature = o.getString("signature", null);
            final String publicKey = o.getString("publicKey", null);
            if (!PluginManifest.isValidPluginId(pluginId) || name == null || name.trim().isEmpty()
                    || apiVersion == null
                    || pkg == null || sha256 == null || !SHA256_HEX.matcher(sha256).matches()
                    || signature == null || publicKey == null
                    || !PluginPackage.isValidPublicKey(publicKey)
                    || !(o.get("versionCode") instanceof Number)) {
                return null;
            }
            final java.util.regex.Matcher api = API_VERSION.matcher(apiVersion);
            if (!api.matches()) {
                return null;
            }
            final int major;
            final int minor;
            try {
                major = Integer.parseInt(api.group(1));
                minor = api.group(2) == null ? 0 : Integer.parseInt(api.group(2));
            } catch (final NumberFormatException e) {
                return null;
            }
            final String packageUrl = resolve(indexUrl, pkg);
            if (packageUrl == null) {
                return null;
            }
            try {
                final int versionCode = WizeStreamPluginApi.readNonNegativeInt(o, "versionCode", 0);
                return new Entry(pluginId, name, versionCode,
                        o.getString("versionName", String.valueOf(versionCode)), major, minor,
                        WizeStreamPluginApi.readHost(o),
                        WizeStreamPluginApi.readMinHostVersionCode(o),
                        WizeStreamPluginApi.readMinAndroidSdk(o),
                        WizeStreamPluginApi.readRequiredCapabilities(o),
                        o.getString("author", null), packageUrl, sha256, signature, publicKey,
                        o.getString("description", null), o.getBoolean("nsfw", false));
            } catch (final PluginException e) {
                return null;
            }
        }

        @Nullable
        private static String resolve(@Nonnull final String base, @Nonnull final String ref) {
            try {
                final URI resolved = new URI(base).resolve(ref);
                final String scheme = resolved.getScheme();
                if (!"https".equals(scheme) && !"http".equals(scheme)) {
                    return null;
                }
                return resolved.toString();
            } catch (final URISyntaxException | IllegalArgumentException e) {
                return null;
            }
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

        @Nonnull
        public String getApiVersion() {
            return apiMajor + "." + apiMinor;
        }

        /**
         * @return the author as declared by the repository, or null. The installed package
         * declares its own author in {@code plugin.json}.
         */
        @Nullable
        public String getAuthor() {
            return author;
        }

        @Nonnull
        public String getPackageUrl() {
            return packageUrl;
        }

        @Nonnull
        public String getSha256() {
            return sha256;
        }

        @Nonnull
        public String getSignature() {
            return signature;
        }

        @Nonnull
        public String getPublicKey() {
            return publicKey;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        public boolean isNsfw() {
            return nsfw;
        }
    }
}
