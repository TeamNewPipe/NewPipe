package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

class VimeoServiceIntegrationTest {
    @Test
    fun vimeoIsAppendedWithoutChangingPersistedServiceIds() {
        val existing = listOf(
            ServiceList.YouTube to 0,
            ServiceList.SoundCloud to 1,
            ServiceList.MediaCCC to 2,
            ServiceList.PeerTube to 3,
            ServiceList.Bandcamp to 4,
            ServiceList.BiliBili to 5,
            ServiceList.NicoNico to 6,
            ServiceList.BitChute to 7,
            ServiceList.Rumble to 8,
            ServiceList.Odysee to 9
        )
        existing.forEach { (service, id) ->
            assertEquals(id, service.serviceId)
            assertSame(service, ServiceList.all()[id])
            assertSame(service, NewPipe.getService(id))
        }
        assertEquals(10, ServiceList.Vimeo.serviceId)
        assertSame(ServiceList.Vimeo, ServiceList.all().last())
        assertSame(ServiceList.Vimeo, NewPipe.getService("Vimeo"))
    }

    @Test
    fun vimeoIsVisibleAndUsesItsOwnNameAndIcon() {
        assertTrue(ServiceHelper.getVisibleServices().contains(ServiceList.Vimeo))
        assertSame(ServiceList.Vimeo, ServiceHelper.getServiceById(10))
        assertEquals("Vimeo", ServiceHelper.getNameOfServiceById(10))
        assertEquals(R.drawable.ic_placeholder_vimeo, ServiceHelper.getIcon(10))
        assertTrue(ServiceList.Vimeo.serviceInfo.mediaCapabilities.contains(StreamingService.ServiceInfo.MediaCapability.AUDIO))
    }

    @Test
    fun signedMediaCacheRefreshesWellBeforeTheObservedOneHourLifetime() {
        assertEquals(5 * 60 * 1000L, ServiceHelper.getCacheExpirationMillis(ServiceList.Vimeo.serviceId))
        assertEquals(60 * 60 * 1000L, ServiceHelper.getCacheExpirationMillis(ServiceList.YouTube.serviceId))
    }

    @Test
    fun publicBrowsingIncludesShowcasesWhileApiSearchRequiresConfiguration() {
        assertFalse(ServiceSearchSupport.supportsSearch(ServiceList.Vimeo.serviceId))
        assertThrows(UnsupportedOperationException::class.java) {
            ServiceSearchSupport.requireSearchFactory(ServiceList.Vimeo.serviceId)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ServiceList.Vimeo.getSearchExtractor("animation")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ServiceList.Vimeo.getSearchExtractor("animation", emptyList(), emptyList())
        }
        assertTrue(ServiceSearchSupport.supportsSearch(ServiceList.YouTube.serviceId))
        assertSame(ServiceList.YouTube.searchQHFactory, ServiceSearchSupport.requireSearchFactory(ServiceList.YouTube.serviceId))
        assertNotNull(ServiceList.Vimeo.playlistLHFactory)
        val kiosks = ServiceList.Vimeo.kioskList
        assertEquals("Staff Picks", kiosks.defaultKioskId)
        assertEquals(setOf("Staff Picks"), kiosks.availableKiosks)
        assertEquals(
            "https://vimeo.com/channels/staffpicks",
            kiosks.getListLinkHandlerFactoryByType("Staff Picks").fromId("Staff Picks").url
        )
    }

    @Test
    fun applicationServiceLookupRoutesPublicCreatorsAndThematicChannels() {
        for (url in listOf(
            "https://vimeo.com/vimeo",
            "https://vimeo.com/vimeo/videos",
            "https://vimeo.com/channels/staffpicks",
            "https://vimeo.com/channels/staffpicks/videos"
        )) {
            assertSame(url, ServiceList.Vimeo, NewPipe.getServiceByUrl(url))
            assertEquals(url, StreamingService.LinkType.CHANNEL, ServiceList.Vimeo.getLinkTypeByUrl(url))
        }
    }

    @Test
    fun applicationServiceLookupRoutesPublicAndEmbeddedVideosToVimeo() {
        for (url in listOf(
            "https://vimeo.com/76979871",
            "https://www.vimeo.com/76979871",
            "https://player.vimeo.com/video/76979871",
            "https://vimeo.com/channels/staffpicks/76979871",
            "https://vimeo.com/groups/shortfilms/videos/76979871",
            "https://vimeo.com/album/123/video/76979871",
            "https://vimeo.com/showcase/123/video/76979871"
        )) {
            assertSame(url, ServiceList.Vimeo, NewPipe.getServiceByUrl(url))
            assertEquals(url, StreamingService.LinkType.STREAM, ServiceList.Vimeo.getLinkTypeByUrl(url))
        }
    }

    @Test
    fun applicationServiceLookupKeepsCollectionsAndRecurringEventsSeparateFromVideoIds() {
        for (url in listOf("https://vimeo.com/showcase/2632481", "https://vimeo.com/album/2632481", "https://vimeo.com/showcase/lean")) {
            assertSame(ServiceList.Vimeo, NewPipe.getServiceByUrl(url))
            assertEquals(StreamingService.LinkType.PLAYLIST, ServiceList.Vimeo.getLinkTypeByUrl(url))
        }
        for (url in listOf("https://vimeo.com/event/4768062", "https://vimeo.com/event/4768062/embed", "https://vimeo.com/event/4753126/videos/1247036786")) {
            assertSame(ServiceList.Vimeo, NewPipe.getServiceByUrl(url))
            assertEquals(StreamingService.LinkType.STREAM, ServiceList.Vimeo.getLinkTypeByUrl(url))
        }
    }
}
