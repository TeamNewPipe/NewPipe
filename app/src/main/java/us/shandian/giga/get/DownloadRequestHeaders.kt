/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.get

import java.io.Serializable
import java.net.URL
import java.util.Collections
import java.util.Locale
import org.schabi.newpipe.extractor.stream.Stream

/** Per-resource request metadata, stored on the mission's stable serialized format. */
object DownloadRequestHeaders {
    @JvmStatic
    fun capture(vararg streams: Stream): ArrayList<HashMap<String, String>> = ArrayList(streams.map { HashMap(it.requestHeaders.orEmpty()) })

    @JvmStatic
    fun copy(headers: List<Map<String, String>>?): ArrayList<HashMap<String, String>>? = headers?.mapTo(ArrayList()) { HashMap(it) }

    @JvmStatic
    fun fromSerialized(value: Serializable?): ArrayList<HashMap<String, String>>? {
        val resources = value as? List<*> ?: return null
        return resources.mapTo(ArrayList()) { resource ->
            val headers = HashMap<String, String>()
            (resource as? Map<*, *>)?.forEach { (key, entry) ->
                if (key is String && entry is String) headers[key] = entry
            }
            headers
        }
    }

    @JvmStatic
    fun at(mission: DownloadMission, resource: Int): Map<String, String> = synchronized(mission) {
        Collections.unmodifiableMap(HashMap(mission.resourceRequestHeaders?.getOrNull(resource).orEmpty()))
    }

    @JvmStatic
    fun replace(mission: DownloadMission, resource: Int, headers: Map<String, String>?) = synchronized(mission) {
        require(resource >= 0 && resource < mission.urls.size)
        val updated = copy(mission.resourceRequestHeaders) ?: ArrayList()
        while (updated.size < mission.urls.size) updated.add(HashMap())
        updated[resource] = HashMap(headers.orEmpty())
        mission.resourceRequestHeaders = updated
    }

    /** Once a redirect changes origin, credentials remain absent on all subsequent hops. */
    @JvmStatic
    fun afterRedirect(headers: Map<String, String>, from: URL, to: URL): Map<String, String> {
        if (sameOrigin(from, to)) return headers
        return headers.filterKeys {
            it.lowercase(Locale.ROOT) !in setOf("authorization", "cookie", "cookie2", "proxy-authorization")
        }
    }

    private fun sameOrigin(first: URL, second: URL): Boolean = first.protocol.equals(second.protocol, true) &&
        first.host.equals(second.host, true) &&
        effectivePort(first) == effectivePort(second) && second.userInfo == null

    private fun effectivePort(url: URL): Int = if (url.port == -1) url.defaultPort else url.port
}
