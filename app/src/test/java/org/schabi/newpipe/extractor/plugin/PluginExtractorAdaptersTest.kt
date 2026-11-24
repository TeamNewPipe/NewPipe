/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType

class PluginExtractorAdaptersTest {
    @Test
    fun builtInItemExtractorsRemainUntouched() {
        val fixture = FixtureItem()
        assertSame(fixture, PluginExtractorAdapters.stream(fixture))
    }

    @Test
    fun foreignImplementationsKeepTheirMetadataAndConcreteMethodErrors() {
        val fixture = FixtureItem()
        val loader = object : ClassLoader(StreamInfoItemExtractor::class.java.classLoader) {}
        val external = Proxy.newProxyInstance(loader, arrayOf(StreamInfoItemExtractor::class.java)) { _, method, args ->
            method.invoke(fixture, *(args ?: emptyArray()))
        } as StreamInfoItemExtractor
        val adapted = PluginExtractorAdapters.stream(external)
        assertNotSame(external, adapted)
        assertEquals("Fixture item", adapted.name)
        val collector = StreamInfoItemsCollector(12345)
        collector.commit(external)
        assertEquals("Fixture item", collector.items.single().name)
        assertNull(collector.items.single().upcomingStatus)
        assertEquals(0, collector.errors.size)

        val broken = Proxy.newProxyInstance(loader, arrayOf(StreamInfoItemExtractor::class.java)) { _, method, args ->
            if (method.name == "getShortDescription") throw AbstractMethodError("A dependency inside the implemented method is missing")
            method.invoke(fixture, *(args ?: emptyArray()))
        } as StreamInfoItemExtractor
        assertThrows(AbstractMethodError::class.java) { PluginExtractorAdapters.stream(broken).shortDescription }
    }

    @Test
    fun foreignImplementationsCanSupplyAnExplicitUpcomingStatus() {
        val fixture = FixtureItem()
        val loader = object : ClassLoader(StreamInfoItemExtractor::class.java.classLoader) {}
        val external = Proxy.newProxyInstance(loader, arrayOf(StreamInfoItemExtractor::class.java)) { _, method, args ->
            if (method.name == "getUpcomingStatus") true else method.invoke(fixture, *(args ?: emptyArray()))
        } as StreamInfoItemExtractor
        val collector = StreamInfoItemsCollector(12345)
        collector.commit(external)
        assertEquals(true, collector.items.single().upcomingStatus)
        assertEquals(0, collector.errors.size)
    }

    private class FixtureItem : StreamInfoItemExtractor {
        override fun getName(): String = "Fixture item"
        override fun getUrl(): String = "https://fixture.example/video"
        override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM
        override fun getDuration(): Long = 30
        override fun getViewCount(): Long = 100
        override fun getUploaderName(): String = "Fixture author"
        override fun getTextualUploadDate(): String? = null
        override fun getUploadDate(): DateWrapper? = null
    }
}
