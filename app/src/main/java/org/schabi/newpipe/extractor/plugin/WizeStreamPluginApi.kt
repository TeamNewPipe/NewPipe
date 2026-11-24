/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.util.Collections

/** Distinguishes native WizeStream packages from the compatible PipePipe SDK. */
object WizeStreamPluginApi {
    const val HOST = "wizestream"
    const val PIPEPIPE_HOST = "pipepipe"
    const val API_VERSION_MAJOR = 2
    const val API_VERSION_MINOR = 0
    const val PROFILE_PREFERENCES = "profile-preferences"

    @JvmStatic
    fun getSupportedCapabilities(): Set<String> = Collections.singleton(PROFILE_PREFERENCES)

    @JvmStatic
    fun isApiCompatible(host: String, major: Int, minor: Int): Boolean = when (host) {
        PIPEPIPE_HOST -> major == PipePipePlugin.API_VERSION_MAJOR && minor in 0..PipePipePlugin.API_VERSION_MINOR
        HOST -> major == API_VERSION_MAJOR && minor in 0..API_VERSION_MINOR
        else -> false
    }

    @JvmStatic
    @Throws(PluginException::class)
    fun requireCompatible(manifest: PluginManifest, hostVersionCode: Int) = requireCompatible(
        manifest.host,
        manifest.apiVersion,
        manifest.isApiCompatible,
        manifest.minHostVersionCode,
        manifest.requiredCapabilities,
        hostVersionCode
    )

    @JvmStatic
    @Throws(PluginException::class)
    fun requireCompatible(entry: PluginRepository.Entry, hostVersionCode: Int) = requireCompatible(
        entry.host,
        entry.apiVersion,
        entry.isApiCompatible,
        entry.minHostVersionCode,
        entry.requiredCapabilities,
        hostVersionCode
    )

    private fun requireCompatible(
        host: String,
        apiVersion: String,
        compatibleApi: Boolean,
        minHostVersionCode: Int,
        requiredCapabilities: List<String>,
        hostVersionCode: Int
    ) {
        if (!compatibleApi) throw PluginException("Plugin requires $host API $apiVersion")
        if (host != HOST) return
        if (hostVersionCode < minHostVersionCode) {
            throw PluginException("Plugin requires WizeStream version code $minHostVersionCode or newer")
        }
        val missing = requiredCapabilities.filterNot { it in getSupportedCapabilities() }
        if (missing.isNotEmpty()) {
            throw PluginException("Plugin requires unsupported WizeStream capabilities: ${missing.joinToString()}")
        }
    }

    @JvmStatic
    @Throws(PluginException::class)
    fun readHost(json: JsonObject): String {
        if (!json.has("host")) return PIPEPIPE_HOST
        val host = json["host"] as? String ?: throw PluginException("Invalid plugin host")
        if (host.isBlank() || host.length > 64) throw PluginException("Invalid plugin host")
        return host
    }

    @JvmStatic
    @Throws(PluginException::class)
    fun readMinHostVersionCode(json: JsonObject): Int = readNonNegativeInt(json, "minHostVersionCode", 0)

    @JvmStatic
    @Throws(PluginException::class)
    fun readMinAndroidSdk(json: JsonObject): Int {
        val sdk = readNonNegativeInt(json, "minAndroidSdk", 24)
        if (sdk !in 1..10000) throw PluginException("Invalid minAndroidSdk")
        return sdk
    }

    @JvmStatic
    @Throws(PluginException::class)
    fun readNonNegativeInt(json: JsonObject, key: String, defaultValue: Int): Int {
        if (!json.has(key)) return defaultValue
        val number = json[key] as? Number ?: throw PluginException("Invalid $key")
        val long = number.toLong()
        if (long !in 0..Int.MAX_VALUE.toLong() || number.toDouble() != long.toDouble()) {
            throw PluginException("Invalid $key")
        }
        return long.toInt()
    }

    @JvmStatic
    @Throws(PluginException::class)
    fun readRequiredCapabilities(json: JsonObject): List<String> {
        if (!json.has("requiredCapabilities")) return emptyList()
        val array = json["requiredCapabilities"] as? JsonArray ?: throw PluginException("Invalid requiredCapabilities")
        if (array.size > 32) throw PluginException("Too many requiredCapabilities")
        val capabilities = array.map { value ->
            val capability = value as? String ?: throw PluginException("Invalid required capability")
            if (capability.isBlank() || capability.length > 64) throw PluginException("Invalid required capability")
            capability
        }.distinct()
        return Collections.unmodifiableList(capabilities)
    }
}
