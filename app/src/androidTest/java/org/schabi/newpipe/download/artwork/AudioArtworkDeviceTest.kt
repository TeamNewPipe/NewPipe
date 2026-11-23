/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download.artwork

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.streams.io.SharpStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.AudioArtworkFinalizer
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.io.FileStream

class AudioArtworkDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun platformReaderFindsOriginalCoverAndUnchangedAudioMetadataInBothFormats() {
        withDirectory { directory ->
            for (format in listOf("mp3", "m4a")) {
                val input = File(directory, "source.$format").apply { writeBytes(fixture("tone.$format")) }
                val output = File(directory, "tagged.$format")
                val cover = fixture("cover.png")
                assertTrue(AudioArtworkTags.embed(input, output, format, AudioArtwork(cover, "image/png")))
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(output.path)
                    assertArrayEquals(cover, retriever.embeddedPicture)
                    assertEquals("Artwork fixture", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                    assertEquals("WizeStream tests", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 0)
                } finally {
                    retriever.release()
                }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(output.path)
                    assertTrue((0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString("mime")!!.startsWith("audio/") })
                } finally {
                    extractor.release()
                }
                assertEquals(1200 to 720, DownloadArtworkImages.dimensions(AudioArtwork(cover, "image/png")))
            }
        }
    }

    @Test fun finalStageWorksThroughDirectAndContentUrisWithoutAnOnlineArtworkRequest() {
        withDirectory { directory ->
            for ((index, format) in listOf("mp3", "m4a").withIndex()) {
                val output = File(directory, "finished.$format").apply { writeBytes(fixture("tone.$format")) }
                val uri = if (index == 0) Uri.fromFile(output) else FileProvider.getUriForFile(context, "${context.packageName}.provider", output)
                val storage = StoredFileHelper(context, null, uri, "audio")
                assertEquals(index == 0, storage.isDirect)
                val cover = fixture("cover.jpg")
                val mission = mission(directory, storage, format, cover)
                AudioArtworkFinalizer.finish(mission)
                assertTrue(mission.isFinished)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(output.path)
                    assertArrayEquals(cover, retriever.embeddedPicture)
                } finally {
                    retriever.release()
                }
                assertTrue(File(directory, "artwork").listFiles().orEmpty().isEmpty())
            }
        }
    }

    @Test fun failedDestinationWriteRestoresAudioAndInterruptedJournalRecoversOnRetry() {
        withDirectory { directory ->
            val bytes = fixture("tone.mp3")
            val output = File(directory, "rollback.mp3").apply { writeBytes(bytes) }
            var failOnce = true
            val storage = object : StoredFileHelper(context, null, Uri.fromFile(output), "audio") {
                override fun openAndTruncateStream(): SharpStream {
                    if (!failOnce) return super.openAndTruncateStream()
                    failOnce = false
                    return object : FileStream(output) {
                        init {
                            setLength(0)
                        }
                        override fun write(buffer: ByteArray, offset: Int, count: Int) {
                            super.write(buffer, offset, minOf(16, count))
                            throw IOException("controlled output failure")
                        }
                    }
                }
            }
            val failed = mission(directory, storage, "mp3", fixture("cover.png"))
            AudioArtworkFinalizer.finish(failed)
            assertTrue(failed.isFinished)
            assertArrayEquals(bytes, output.readBytes())

            val recovering = mission(directory, StoredFileHelper(context, null, Uri.fromFile(output), "audio"), "mp3", null)
            val work = File(directory, "artwork").apply { mkdirs() }
            File(work, recovering.metadata.name + ".original").writeBytes(bytes)
            File(work, recovering.metadata.name + ".writing").writeText(recovering.storage.uri.toString())
            output.writeBytes(byteArrayOf(1, 2, 3))
            AudioArtworkFinalizer.finish(recovering)
            assertTrue(recovering.isFinished)
            assertArrayEquals(bytes, output.readBytes())
            assertTrue(work.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun coverCapturedBeforeConversionSurvivesAChangeOfAudioContainer() {
        withDirectory { directory ->
            val original = File(directory, "source.m4a").apply { writeBytes(fixture("tone.m4a")) }
            val converted = File(directory, "converted.mp3")
            val cover = fixture("cover.png")
            assertTrue(AudioArtworkTags.embed(original, converted, "m4a", AudioArtwork(cover, "image/png")))
            val mission = mission(directory, StoredFileHelper(context, null, Uri.fromFile(converted), "audio"), "mp3", null)
            mission.artworkRequest.capturedSourceCover = false
            AudioArtworkFinalizer.captureSourceCover(mission)
            assertArrayEquals(cover, mission.artworkRequest.sourceCover!!.bytes)
            // Existing conversion/demux produces a new container; the captured source cover remains.
            converted.writeBytes(fixture("tone.mp3"))
            AudioArtworkFinalizer.finish(mission)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(converted.path)
                assertArrayEquals(cover, retriever.embeddedPicture)
            } finally {
                retriever.release()
            }
        }
    }

    @Test fun cancellingAfterTheFinalWriteDeletesOrRestoresTheFileWithoutRecreatingIt() {
        withDirectory { directory ->
            for (alsoDeleteFile in listOf(true, false)) {
                val bytes = fixture("tone.mp3")
                val output = File(directory, "cancel-$alsoDeleteFile.mp3").apply { writeBytes(bytes) }
                lateinit var download: DownloadMission
                var firstWrite = true
                val storage = object : StoredFileHelper(context, null, Uri.fromFile(output), "audio") {
                    override fun getStream(): SharpStream {
                        assertTrue("Opening must be atomic with mission cancellation", Thread.holdsLock(download))
                        return super.getStream()
                    }

                    override fun openAndTruncateStream(): SharpStream {
                        assertTrue("Truncation must be atomic with mission cancellation", Thread.holdsLock(download))
                        if (!firstWrite) return super.openAndTruncateStream()
                        firstWrite = false
                        return object : FileStream(output) {
                            init {
                                setLength(0)
                            }

                            override fun close() {
                                super.close()
                                // Cancel exactly after writing and before output verification.
                                download.cancel(alsoDeleteFile)
                            }
                        }
                    }
                }
                download = mission(directory, storage, "mp3", fixture("cover.png"))
                AudioArtworkFinalizer.finish(download)
                assertFalse("Cancelled artwork must not publish completion", download.isFinished)
                if (alsoDeleteFile) assertFalse(output.exists()) else assertArrayEquals(bytes, output.readBytes())
                assertTrue(File(directory, "artwork").listFiles().orEmpty().isEmpty())
            }
        }
    }

    @Test fun artworkSelectionUsesActualResolutionAndKeepsOriginalBytesAfterFailures() {
        val small = AudioArtwork(fixture("cover.png"), "image/png")
        val large = AudioArtwork(fixture("cover.jpg"), "image/jpeg")
        val loaded = mutableListOf<String>()
        val selected = DownloadArtworkImages.chooseBest(listOf("missing", "small", "malformed", "large"), { false }) { candidate ->
            loaded += candidate
            when (candidate) {
                "missing" -> throw IOException("unavailable image")
                "small" -> small
                "malformed" -> AudioArtwork(byteArrayOf(1, 2, 3), "image/png")
                else -> large
            }
        }
        assertEquals(4, loaded.size)
        assertArrayEquals(large.bytes, selected!!.bytes)
        assertEquals(1600 to 900, DownloadArtworkImages.dimensions(selected))
        val cancelled = DownloadArtworkImages.chooseBest(listOf("unused"), { true }) { throw AssertionError("Cancelled work must not fetch an image") }
        assertEquals(null, cancelled)
    }

    private fun mission(directory: File, storage: StoredFileHelper, format: String, cover: ByteArray?): DownloadMission = DownloadMission(arrayOf("https://example.invalid/audio"), storage, 'a', null).apply {
        current = urls.size
        metadata = File(directory, "pending-${System.nanoTime()}")
        artworkRequest = DownloadArtworkRequest(format, arrayListOf()).apply {
            capturedSourceCover = true
            sourceCover = cover?.let { AudioArtwork(it, if (it[0].toInt() and 255 == 255) "image/jpeg" else "image/png") }
        }
    }
    private fun fixture(name: String): ByteArray = Base64.decode(InstrumentationRegistry.getInstrumentation().context.assets.open("audio-artwork/$name.base64").use { it.readBytes() }, Base64.DEFAULT)
    private fun withDirectory(block: (File) -> Unit) {
        val directory = File(context.cacheDir, "update/artwork-${System.nanoTime()}").apply { mkdirs() }
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
