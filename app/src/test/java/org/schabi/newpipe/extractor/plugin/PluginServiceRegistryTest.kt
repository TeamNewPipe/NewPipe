/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList

class PluginServiceRegistryTest {
    @After
    fun removeTestServices() {
        ServiceList.unregister(123450)
        ServiceList.unregister(123451)
    }

    @Test
    fun builtInIdentitiesArePreservedAndCannotBeRemovedOrReplaced() {
        assertEquals((0..10).toList(), ServiceList.builtIn().map { it.serviceId })
        assertEquals(listOf("YouTube", "SoundCloud", "media.ccc.de", "PeerTube", "Bandcamp", "BiliBili", "NicoNico", "BitChute", "Rumble", "Odysee", "Vimeo"), ServiceList.builtIn().map { it.serviceInfo.name })
        val original = ServiceList.getById(0)
        ServiceList.unregister(0)
        assertSame(original, ServiceList.getById(0))
        assertThrows(IllegalArgumentException::class.java) { ServiceList.register(service(0, "Different name")) }
        for (name in listOf("YouTube", "youtube", " YouTube ", "VIMEO")) {
            assertThrows(IllegalArgumentException::class.java) { ServiceList.register(service(123450, name)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServiceList.register(PluginContractTest.NamedService(123450, "Renamed provider", "http://WWW.YOUTUBE.COM:80/"))
        }
    }

    @Test
    fun sparseDynamicIdsResolveByIdentityAndPublishImmutableSnapshots() {
        val before = ServiceList.all()
        val plugin = service(123450, "Fixture provider")
        ServiceList.register(plugin)
        assertFalse(before.contains(plugin))
        assertSame(plugin, NewPipe.getService(123450))
        assertSame(plugin, ServiceList.getById(123450))
        assertTrue(ServiceList.all().contains(plugin))
        assertThrows(UnsupportedOperationException::class.java) { ServiceList.all().clear() }
        assertThrows(IllegalArgumentException::class.java) { ServiceList.register(service(123450, "Other provider")) }
        assertThrows(IllegalArgumentException::class.java) { ServiceList.register(service(123451, " fixture PROVIDER ")) }
        ServiceList.unregister(123450)
        assertEquals(null, ServiceList.getById(123450))
        assertEquals(11, ServiceList.builtIn().size)
    }

    private fun service(id: Int, name: String) = PluginContractTest.NamedService(id, name)
}
