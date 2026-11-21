/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class DownloadedAvailabilityIndexTest {
    @Test
    fun onlyCompletedPresentVideoFilesQualify() {
        val source = "https://example.org/video"
        assertTrue(DownloadedAvailabilityIndex.isCompleteVideo('v', 100, true, 100, source))
        assertFalse(DownloadedAvailabilityIndex.isCompleteVideo('v', 100, true, 99, source))
        assertFalse(DownloadedAvailabilityIndex.isCompleteVideo('v', 0, true, 100, source))
        assertFalse(DownloadedAvailabilityIndex.isCompleteVideo('v', 100, false, 100, source))
        assertFalse(DownloadedAvailabilityIndex.isCompleteVideo('a', 100, true, 100, source))
        assertFalse(DownloadedAvailabilityIndex.isCompleteVideo('v', 100, true, 100, ""))
        val segmented = DownloadedAvailabilityIndex.isCompleteVideo(
            'v',
            100,
            true,
            100,
            "$source?wizestream-segments=10-20"
        )
        assertFalse(segmented)
    }

    @Test
    fun identitiesAreCanonicalAndRejectSegments() {
        val youtube = ServiceList.YouTube.serviceId
        val videoId = DownloadedAvailabilityIndex.identity(
            youtube,
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        assertNotNull(videoId)
        assertEquals(
            DownloadedAvailabilityIndex.identity(youtube, "https://youtu.be/dQw4w9WgXcQ"),
            DownloadedAvailabilityIndex.identity(youtube, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            DownloadedAvailabilityIndex.identity(77, "https://example.org/a/../video"),
            DownloadedAvailabilityIndex.identity(77, "https://example.org/video")
        )
        assertNull(DownloadedAvailabilityIndex.identity(77, ""))
        assertNull(DownloadedAvailabilityIndex.identity(77, "https://example.org?v=1&wizestream-segments=1"))
    }
}
