/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.download.artwork.AudioArtwork
import org.schabi.newpipe.download.artwork.AudioArtworkTags

class LocalMediaMetadataCacheDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cachedFileAndContentMetadataRefreshAfterAnInPlaceEditWithoutChangingArtwork() {
        val directory = File(context.cacheDir, "update/metadata-cache-${System.nanoTime()}").apply { mkdirs() }
        LocalMediaMetadataLoader.invalidateCache()
        try {
            val input = File(directory, "source.mp3").apply { writeBytes(fixture("tone.mp3")) }
            val media = File(directory, "tagged.mp3")
            val cover = fixture("cover.png")
            assertTrue(AudioArtworkTags.embed(input, media, "mp3", AudioArtwork(cover, "image/png")))
            val fileUri = Uri.fromFile(media).toString()
            val alias = FileProvider.getUriForFile(context, "${context.packageName}.provider", media).toString()
            val originalBytes = media.readBytes()
            for (uri in listOf(fileUri, alias)) {
                val first = LocalMediaMetadataLoader.readCached(context.contentResolver, uri, "audio/mpeg")
                assertEquals("Artwork fixture", first.title)
                assertArrayEquals(cover, first.artwork)
                assertSame(first, LocalMediaMetadataLoader.readCached(context.contentResolver, uri, "audio/mpeg"))
            }
            assertArrayEquals("Reading metadata must preserve the media file", originalBytes, media.readBytes())

            val originalIdentity = OfflinePlaybackMetadata.identity(context, fileUri)?.signature
            val oldTitle = "Artwork fixture".toByteArray(Charsets.UTF_8)
            val newTitle = "Changed fixture".toByteArray(Charsets.UTF_8)
            val offset = originalBytes.indices.firstOrNull { start ->
                start <= originalBytes.size - oldTitle.size && oldTitle.indices.all { originalBytes[start + it] == oldTitle[it] }
            }
            assertTrue("Fixture title was not found", offset != null)
            val edited = originalBytes.copyOf().apply { newTitle.copyInto(this, offset!!) }
            val modified = media.lastModified()
            media.writeBytes(edited)
            media.setLastModified(modified)
            assertEquals(originalBytes.size.toLong(), media.length())
            assertNotEquals(originalIdentity, OfflinePlaybackMetadata.identity(context, fileUri)?.signature)
            for (uri in listOf(fileUri, alias)) {
                val refreshed = LocalMediaMetadataLoader.readCached(context.contentResolver, uri, "audio/mpeg")
                assertEquals("Changed fixture", refreshed.title)
                assertArrayEquals(cover, refreshed.artwork)
            }
            assertArrayEquals("Caching must not rewrite the original artwork", edited, media.readBytes())

            LocalMediaMetadataLoader.invalidateCache()
            assertArrayEquals(cover, LocalMediaMetadataLoader.readCached(context.contentResolver, alias, "audio/mpeg").artwork)
            assertTrue(media.delete())
            assertEquals(LocalMediaEmbeddedMetadata(), LocalMediaMetadataLoader.readCached(context.contentResolver, alias, "audio/mpeg"))
        } finally {
            LocalMediaMetadataLoader.invalidateCache()
            directory.deleteRecursively()
        }
    }

    private fun fixture(name: String): ByteArray = Base64.decode(
        InstrumentationRegistry.getInstrumentation().context.assets.open("audio-artwork/$name.base64").use { it.readBytes() },
        Base64.DEFAULT
    )
}
