/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.local.media.chapters.ChapterFixtures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EmbeddedOfflineChaptersTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun missingFingerprintDoesNotSuppressIndependentlyReadableChapters() {
        val file = File.createTempFile("embedded-without-identity", ".mp4", context.cacheDir)
        try {
            file.writeBytes(ChapterFixtures.real("nero.mp4"))
            val result = ResolvedOfflineMedia("", emptyList(), emptyList(), null).withEmbeddedChapters(context, Uri.fromFile(file).toString())
            assertEquals(listOf(0L, 1500L, 3500L), result.chapters.map(ChapterTimes::startMillis))
            assertEquals("", result.identity)
            assertNull(result.sourceInfo)
        } finally {
            file.delete()
        }
    }

    @Test
    fun fallbackKeepsStaleDownloadAndCaptionIdentityDecisionsIntact() {
        val file = File.createTempFile("embedded-stale-identity", ".mp4", context.cacheDir)
        try {
            file.writeBytes(ChapterFixtures.real("nero.mp4"))
            val before = ResolvedOfflineMedia("current-file", emptyList(), emptyList(), null, staleDownload = true)
            val result = before.withEmbeddedChapters(context, Uri.fromFile(file).toString())
            assertEquals(3, result.chapters.size)
            assertTrue(result.staleDownload)
            assertNull(result.sourceInfo)
            assertEquals(before.identity, result.identity)
            assertSame(before.captionIdentities, result.captionIdentities)
        } finally {
            file.delete()
        }
    }
}
