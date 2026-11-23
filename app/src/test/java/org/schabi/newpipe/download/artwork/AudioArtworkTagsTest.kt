/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download.artwork

import java.io.File
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioArtworkTagsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun mp3ArtworkPreservesAudioAndExistingTextFrames() {
        val original = fixture("tone.mp3")
        val input = file("input.mp3", original)
        val output = File(temporary.root, "output.mp3")
        val image = fixture("cover.png")
        assertTrue(AudioArtworkTags.embed(input, output, "mp3", AudioArtwork(image, "image/png")))
        val result = output.readBytes()
        assertEquals(3, result[3].toInt())
        assertArrayEquals(original.copyOfRange(id3End(original), original.size), result.copyOfRange(id3End(result), result.size))
        assertArrayEquals(original.copyOfRange(10, id3End(original)), result.copyOfRange(id3End(result) - (id3End(original) - 10), id3End(result)))
        assertTrue(result.toString(Charsets.ISO_8859_1).contains("Preserve this field"))
        assertArrayEquals(image, read(result, "mp3")!!.bytes)
        assertArrayEquals(original, input.readBytes())
    }

    @Test fun mp4KeepsMediaOffsetsAndEveryOriginalAtomExceptOldMoovType() {
        val original = fixture("tone.m4a")
        val input = file("input.m4a", original)
        val output = File(temporary.root, "output.m4a")
        val image = fixture("cover.jpg")
        assertTrue(AudioArtworkTags.embed(input, output, "m4a", AudioArtwork(image, "image/jpeg")))
        val result = output.readBytes()
        val moov = topAtoms(original).single { it.second == "moov" }.first
        val expectedPrefix = original.copyOf().apply { "free".toByteArray().copyInto(this, moov + 4) }
        assertArrayEquals(expectedPrefix, result.copyOfRange(0, original.size))
        val mdat = topAtoms(original).single { it.second == "mdat" }.first
        val mdatSize = ByteBuffer.wrap(original, mdat, 4).int
        assertArrayEquals(original.copyOfRange(mdat, mdat + mdatSize), result.copyOfRange(mdat, mdat + mdatSize))
        assertTrue(result.copyOfRange(original.size, result.size).toString(Charsets.ISO_8859_1).contains("Preserve this field"))
        assertArrayEquals(image, read(result, "m4a")!!.bytes)
    }

    @Test fun existingCoversRemainUnchangedAndNoReplacementIsWritten() {
        for (format in listOf("mp3", "m4a")) {
            val input = file("original.$format", fixture("tone.$format"))
            val tagged = File(temporary.root, "tagged.$format")
            val cover = fixture("cover.png")
            assertTrue(AudioArtworkTags.embed(input, tagged, format, AudioArtwork(cover, "image/png")))
            val previous = tagged.readBytes()
            val replacement = File(temporary.root, "replacement.$format")
            assertFalse(AudioArtworkTags.embed(tagged, replacement, format, AudioArtwork(fixture("cover.jpg"), "image/jpeg")))
            assertFalse(replacement.exists())
            assertArrayEquals(previous, tagged.readBytes())
            assertArrayEquals(cover, read(previous, format)!!.bytes)
        }
    }

    @Test fun directMp3WithoutId3AndVersionFourUnicodeTagsAreSupported() {
        val fixture = fixture("tone.mp3")
        val audio = fixture.copyOfRange(id3End(fixture), fixture.size)
        val title = byteArrayOf(3) + "موسيقى Café".toByteArray()
        val frame = "TIT2".toByteArray() + synchsafe(title.size) + byteArrayOf(0, 0) + title
        val tag = byteArrayOf(73, 68, 51, 4, 0, 0) + synchsafe(frame.size) + frame
        for ((index, bytes) in listOf(audio, tag + audio).withIndex()) {
            val input = file("direct$index.mp3", bytes)
            val output = File(temporary.root, "direct-tagged$index.mp3")
            assertTrue(AudioArtworkTags.embed(input, output, "mp3", AudioArtwork(fixture("cover.jpg"), "image/jpeg")))
            val result = output.readBytes()
            assertArrayEquals(audio, result.copyOfRange(id3End(result), result.size))
            if (index == 1) assertTrue(result.toString(Charsets.UTF_8).contains("موسيقى Café"))
        }
    }

    @Test fun legacyVersionTwoUsesPicAndKeepsTheTitle() {
        val fixture = fixture("tone.mp3")
        val audio = fixture.copyOfRange(id3End(fixture), fixture.size)
        val title = byteArrayOf(0) + "Old title".toByteArray()
        val frame = "TT2".toByteArray() + byteArrayOf(0, 0, title.size.toByte()) + title
        val input = file("old.mp3", byteArrayOf(73, 68, 51, 2, 0, 0) + synchsafe(frame.size) + frame + audio)
        val output = File(temporary.root, "legacy.mp3")
        val cover = fixture("cover.jpg")
        assertTrue(AudioArtworkTags.embed(input, output, "mp3", AudioArtwork(cover, "image/jpeg")))
        val result = output.readBytes()
        assertEquals("PIC", String(result, 10, 3))
        assertArrayEquals(cover, read(result, "mp3")!!.bytes)
        assertTrue(result.toString(Charsets.ISO_8859_1).contains("Old title"))
    }

    @Test fun sizeToEndMdatIsBoundedBeforeAppendingMetadata() {
        val original = fixture("tone-faststart.m4a")
        val atoms = topAtoms(original)
        val mdat = atoms.single { it.second == "mdat" }.first
        val mdatSize = ByteBuffer.wrap(original, mdat, 4).int
        assertEquals(original.size, mdat + mdatSize)
        // Size zero is valid for this last atom; all real sample offsets remain unchanged.
        val unbounded = original.copyOf().apply { ByteBuffer.wrap(this, mdat, 4).putInt(0) }
        val input = file("to-end.m4a", unbounded)
        val output = File(temporary.root, "bounded.m4a")
        assertTrue(AudioArtworkTags.embed(input, output, "m4a", AudioArtwork(fixture("cover.png"), "image/png")))
        assertEquals(mdatSize, ByteBuffer.wrap(output.readBytes(), mdat, 4).int)
        assertEquals("moov", topAtoms(output.readBytes()).last().second)
    }

    @Test fun malformedLengthsAndCancelledWorkNeverModifyTheInput() {
        val original = fixture("tone.m4a")
        val malformed = original.copyOf().apply { ByteBuffer.wrap(this, 0, 4).putInt(Int.MAX_VALUE) }
        val input = file("malformed.m4a", malformed)
        assertThrows(IllegalArgumentException::class.java) { AudioArtworkTags.embed(input, File(temporary.root, "bad.m4a"), "m4a", AudioArtwork(fixture("cover.png"), "image/png")) }
        assertArrayEquals(malformed, input.readBytes())
        val good = file("cancel.mp3", fixture("tone.mp3"))
        val before = good.readBytes()
        assertThrows(InterruptedIOException::class.java) { AudioArtworkTags.embed(good, File(temporary.root, "cancelled.mp3"), "mp3", AudioArtwork(fixture("cover.png"), "image/png")) { true } }
        assertArrayEquals(before, good.readBytes())
    }

    private fun fixture(name: String): ByteArray = Base64.getMimeDecoder().decode(checkNotNull(javaClass.getResourceAsStream("/audio-artwork/$name.base64")).use { it.readBytes() })
    private fun file(name: String, bytes: ByteArray): File = File(temporary.root, name).apply { writeBytes(bytes) }
    private fun id3End(bytes: ByteArray): Int = 10 + (6..9).fold(0) { result, index -> (result shl 7) or (bytes[index].toInt() and 127) }
    private fun synchsafe(value: Int) = byteArrayOf((value ushr 21).toByte(), ((value ushr 14) and 127).toByte(), ((value ushr 7) and 127).toByte(), (value and 127).toByte())
    private fun read(bytes: ByteArray, format: String): AudioArtwork? = AudioArtworkTags.readCover(
        object : AudioArtworkTags.Source {
            override val length: Long get() = bytes.size.toLong()
            override fun read(position: Long, size: Int): ByteArray = bytes.copyOfRange(position.toInt(), position.toInt() + size)
        },
        format
    )
    private fun topAtoms(bytes: ByteArray): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var offset = 0
        while (offset < bytes.size) {
            result += offset to String(bytes, offset + 4, 4)
            offset += ByteBuffer.wrap(bytes, offset, 4).int
        }
        return result
    }
}
