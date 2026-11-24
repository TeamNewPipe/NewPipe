/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.newpipe.app.model.PeertubeInstance

/**
 * Preference keys, defaults and the stored JSON format for the PeerTube instances screen
 */
object PeertubePreferences {
    const val KEY_SELECTED_INSTANCE = "peertube_selected_instance"
    const val KEY_INSTANCE_LIST = "peertube_instance_list"

    const val KEY_MAIN_PAGE_CHANGE = "key_main_page_change"

    const val INSTANCE_LIST_URL = "https://joinpeertube.org/instances#instances-list"

    val DEFAULT_INSTANCE = PeertubeInstance(name = "FramaTube", url = "https://framatube.org")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Adds https when no scheme is given and drops a trailing slash.
     *
     * @return the cleaned URL, or null when it isn't an https URL
     */
    fun cleanInstanceUrl(input: String): String? {
        val trimmed = input.trim()
        val withScheme = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
        return withScheme.removeSuffix("/").takeIf { url -> url.startsWith("https://") }
    }

    fun encodeInstance(instance: PeertubeInstance): String = json.encodeToString(instance)

    fun decodeInstance(value: String): PeertubeInstance? = runCatching {
        json.decodeFromString<PeertubeInstance>(value)
    }.getOrNull()

    fun encodeInstanceList(instances: List<PeertubeInstance>): String = json
        .encodeToString(InstanceList(instances))

    fun decodeInstanceList(value: String): List<PeertubeInstance>? = runCatching {
        json.decodeFromString<InstanceList>(value).instances
    }.getOrNull()

    @Serializable
    private class InstanceList(val instances: List<PeertubeInstance>)
}
