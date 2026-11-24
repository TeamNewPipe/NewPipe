/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.plugin

import java.net.URI
import java.security.MessageDigest
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginManifest
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginApi

/** Installation rules shared by the repository UI and the package installer. */
internal object PluginInstallPolicy {
    /** Newest usable version wins across repositories; equal versions retain repository order. */
    fun preferredEntries(
        entries: List<Pair<String, PluginRepository.Entry>>,
        hostVersionCode: Int,
        androidSdk: Int = 24
    ): List<Pair<String, PluginRepository.Entry>> = entries
        .groupBy { it.second.pluginId to it.second.publicKey }
        .values.map { candidates ->
            val compatible = candidates.filter { (_, entry) ->
                runCatching { requireCompatible(entry, hostVersionCode, androidSdk) }.isSuccess
            }
            compatible.ifEmpty { candidates }.maxByOrNull { it.second.versionCode }!!
        }

    fun requireCompatible(entry: PluginRepository.Entry, hostVersionCode: Int, androidSdk: Int) {
        WizeStreamPluginApi.requireCompatible(entry, hostVersionCode)
        requireAndroid(entry.pluginId, entry.versionCode, entry.sha256, entry.minAndroidSdk, androidSdk)
    }

    fun requireCompatible(manifest: PluginManifest, sha256: String, hostVersionCode: Int, androidSdk: Int) {
        WizeStreamPluginApi.requireCompatible(manifest, hostVersionCode)
        requireAndroid(manifest.pluginId, manifest.versionCode, sha256, manifest.minAndroidSdk, androidSdk)
    }

    private fun requireAndroid(pluginId: String, versionCode: Int, sha256: String, declared: Int, androidSdk: Int) {
        // This published archive invokes URLEncoder.encode(String, Charset), added in API 33.
        // Scope the exception to its verified bytes; version 5 uses the older Android overload.
        val knownTwitch4 = pluginId == "mohr.jonas.twitch" && versionCode == 4 && sha256.equals(
            "f44371de1ec69c32fbf37cd61f5ce00d2b676005233437b0d1fad3fdb1263681",
            ignoreCase = true
        )
        val minimum = maxOf(24, declared, if (knownTwitch4) 33 else 24)
        if (androidSdk < minimum) {
            val message = if (knownTwitch4 && minimum == 33) {
                "This plugin version requires Android 13 or newer. Install a newer plugin release."
            } else {
                "This plugin version requires Android API $minimum or newer"
            }
            throw PluginException(message)
        }
    }

    private val officialBuiltIns = mapOf(
        "org.pipepipe.plugin.soundcloud" to 1,
        "org.pipepipe.plugin.mediaccc" to 2,
        "org.pipepipe.plugin.peertube" to 3,
        "org.pipepipe.plugin.bandcamp" to 4
    )

    fun builtInName(pluginId: String, name: String, builtIns: Map<Int, String>): String? {
        officialBuiltIns[pluginId]?.let { builtIns[it]?.let { value -> return value } }
        return builtIns.values.firstOrNull { it.trim().equals(name.trim(), ignoreCase = true) }
    }

    /** Matches PipePipe's deterministic allocation, regardless of installation order. */
    fun serviceId(pluginId: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(pluginId.toByteArray(Charsets.UTF_8))
        val hash = ((digest[0].toInt() and 0x7f) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or (digest[3].toInt() and 0xff)
        return 1000 + hash % (Int.MAX_VALUE - 1000)
    }

    fun requireHttps(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw PluginException("Invalid repository or package URL", e)
        }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null
        ) {
            throw PluginException("Only HTTPS URLs without credentials or fragments are supported")
        }
        return uri.toASCIIString()
    }

    fun checkPublisher(pinnedKey: String?, publicKey: String) {
        if (pinnedKey != null && pinnedKey != publicKey) {
            throw PluginException("The publisher key differs from the key trusted on first installation")
        }
    }

    fun checkManifest(
        entry: PluginRepository.Entry,
        manifest: PluginManifest,
        builtIns: Map<Int, String>
    ) {
        if (manifest.pluginId != entry.pluginId || manifest.name != entry.name ||
            manifest.versionCode != entry.versionCode || manifest.versionName != entry.versionName ||
            manifest.apiVersion != entry.apiVersion || manifest.host != entry.host ||
            manifest.minHostVersionCode != entry.minHostVersionCode ||
            manifest.minAndroidSdk != entry.minAndroidSdk ||
            manifest.requiredCapabilities.toSet() != entry.requiredCapabilities.toSet() ||
            (entry.author != null && manifest.author != entry.author)
        ) {
            throw PluginException("Package does not match repository entry")
        }
        val builtIn = builtInName(manifest.pluginId, manifest.name, builtIns)
            ?: builtIns[manifest.legacyServiceId]
        if (builtIn != null) throw PluginException("$builtIn is already built into WizeStream")
        if (!manifest.isApiCompatible) {
            throw PluginException("Plugin requires API ${manifest.apiVersion}")
        }
    }

    fun needsProfileRestart(loadedProfile: String?, activeProfile: String, hasLoadedPlugins: Boolean): Boolean = hasLoadedPlugins && loadedProfile != null && loadedProfile != activeProfile

    /** A reinstall can change package bytes without changing its publisher's version code. */
    fun needsPackageRestart(enabled: Boolean, installed: Map<String, String>, loaded: Map<String, String>): Boolean {
        val expected = if (enabled) installed else emptyMap()
        return expected.size != loaded.size || expected.any { (pluginId, hash) ->
            !hash.equals(loaded[pluginId], ignoreCase = true)
        }
    }
}
