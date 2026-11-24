/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import java.net.URI
import java.util.Locale
import org.schabi.newpipe.extractor.StreamingService

/** Cheap duplicate-provider detection; never resolves hosts or makes requests. */
object PluginServiceIdentity {
    @JvmStatic
    fun normalizedBaseUrl(service: StreamingService): String? {
        return try {
            val uri = URI(service.baseUrl).normalize()
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
            val defaultPort = if (scheme == "https") 443 else 80
            val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            "$host$port$path$query"
        } catch (_: Exception) {
            null
        }
    }
}
