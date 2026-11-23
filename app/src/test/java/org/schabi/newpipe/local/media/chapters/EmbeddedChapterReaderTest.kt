/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media.chapters

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileDescriptor
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class EmbeddedChapterReaderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var file: File
    private lateinit var provider: ChapterProvider
    private val uri = Uri.parse("content://embedded-chapter-fixtures/audio")

    @Before
    fun setUp() {
        file = File.createTempFile("embedded-chapters", ".media", context.cacheDir)
        provider = ChapterProvider(file)
        provider.attachInfo(context, ProviderInfo().apply { authority = uri.authority })
        ShadowContentResolver.registerProviderInternal(uri.authority, provider)
    }

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun contentUriReadsOnlyItsAssetSliceAndClosesItsReadOnlyDescriptor() {
        val media = ChapterFixtures.real("id3v23.mp3")
        val original = ByteArray(57) { 0x77 } + media + ByteArray(33) { 0x55 }
        file.writeBytes(original)
        provider.offset = 57
        provider.length = media.size.toLong()
        assertEquals(ChapterFixtures.expected, EmbeddedChapterReader.read(context, uri, 4000))
        assertEquals("r", provider.lastMode)
        assertFalse(checkNotNull(provider.lastDescriptor).valid())
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun fileUrisWorkWithoutAContentProviderOrNetworkRequest() {
        file.writeBytes(ChapterFixtures.real("nero.m4a"))
        assertEquals(ChapterFixtures.expected, EmbeddedChapterReader.read(context, Uri.fromFile(file), 4000))
        assertEquals(0, provider.opens)
        assertTrue(EmbeddedChapterReader.read(context, Uri.parse("https://example.invalid/audio.mp3"), 4000).isEmpty())
        assertEquals(0, provider.opens)
    }

    @Test
    fun assetBoundsCannotBorrowChapterBytesFromOutsideTheDeclaredSlice() {
        file.writeBytes(ChapterFixtures.real("id3v24.mp3"))
        provider.length = 10
        assertTrue(EmbeddedChapterReader.read(context, uri, 4000).isEmpty())
        assertFalse(checkNotNull(provider.lastDescriptor).valid())
    }

    @Test
    fun missingTitleUsesALocalizedLabelInsteadOfInternalElementId() {
        file.writeBytes(ChapterFixtures.id3(ChapterFixtures.chapter("private-element-id", 1000, "")))
        val chapter = EmbeddedChapterReader.read(context, uri, 4000).single()
        assertEquals(1000L, chapter.startMillis)
        assertEquals(context.getString(R.string.embedded_chapter_fallback_title, 1), chapter.title)
    }

    @Test
    fun revokedGrantAndMissingFileDoNotBlockPlayback() {
        file.writeBytes(ChapterFixtures.real("id3v24.mp3"))
        provider.denied = true
        assertTrue(EmbeddedChapterReader.read(context, uri, 4000).isEmpty())
        provider.denied = false
        file.delete()
        assertTrue(EmbeddedChapterReader.read(context, uri, 4000).isEmpty())
    }

    @Test
    fun preCancelledReadsDoNotOpenTheProvider() {
        file.writeBytes(ChapterFixtures.real("id3v24.mp3"))
        val cancellation = CancellationSignal().apply { cancel() }
        assertTrue(EmbeddedChapterReader.read(context, uri, 4000, cancellation).isEmpty())
        assertEquals(0, provider.opens)
    }

    @Test
    fun completedReadsReleaseTheirCancellationListener() {
        file.writeBytes(ChapterFixtures.real("id3v24.mp3"))
        val cancellation = CancellationSignal()
        assertEquals(ChapterFixtures.expected, EmbeddedChapterReader.read(context, uri, 4000, cancellation))
        assertFalse(checkNotNull(provider.lastDescriptor).valid())
        // ContentResolver returns its own AssetFileDescriptor around the provider's descriptor.
        // Counting close() calls on the provider's asset cannot observe client-side cleanup.
        assertNull(
            "The completed read must release the listener that captures its owned descriptor",
            ReflectionHelpers.getField<CancellationSignal.OnCancelListener?>(cancellation, "mOnCancelListener")
        )
        cancellation.cancel()
        assertFalse(checkNotNull(provider.lastDescriptor).valid())
    }

    private class ChapterProvider(private val file: File) : ContentProvider() {
        var offset = 0L
        var length = AssetFileDescriptor.UNKNOWN_LENGTH
        var denied = false
        var opens = 0
        var lastMode: String? = null
        var lastDescriptor: FileDescriptor? = null

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            opens++
            lastMode = mode
            if (denied) throw SecurityException("Grant revoked")
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), offset, length).also {
                lastDescriptor = it.fileDescriptor
            }
        }

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String = "audio/mpeg"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
