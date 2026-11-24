/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.newpipe.app.model.PeertubeInstance
import net.newpipe.app.preferences.PeertubePreferences.DEFAULT_INSTANCE

class PeertubePreferencesTest {

    @Test
    fun addsHttpsAndDropsTrailingSlash() {
        assertEquals("https://framatube.org", PeertubePreferences.cleanInstanceUrl(" framatube.org/ "))
    }

    @Test
    fun rejectsPlainHttp() {
        assertNull(PeertubePreferences.cleanInstanceUrl("http://framatube.org"))
    }

    @Test
    fun readsTheListWrittenByTheLegacyScreen() {
        val legacyJson = """{"instances":[{"name":"FramaTube","url":"https://framatube.org"}]}"""
        assertEquals(listOf(DEFAULT_INSTANCE), PeertubePreferences.decodeInstanceList(legacyJson))
    }

    @Test
    fun instanceListSurvivesARoundTrip() {
        val instances = listOf(DEFAULT_INSTANCE, PeertubeInstance(name = "Tilvids", url = "https://tilvids.com"))
        val encoded = PeertubePreferences.encodeInstanceList(instances)
        assertEquals(instances, PeertubePreferences.decodeInstanceList(encoded))
    }

    @Test
    fun brokenJsonIsIgnored() {
        assertNull(PeertubePreferences.decodeInstanceList("not json"))
        assertNull(PeertubePreferences.decodeInstance("not json"))
    }
}
