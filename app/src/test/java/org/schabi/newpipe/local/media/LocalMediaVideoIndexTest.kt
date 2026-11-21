/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaVideoIndexTest {
    @Test
    fun `folders use volume and complete path identities`() {
        val groups = LocalMediaVideoIndex.folders(
            listOf(
                video(1, volume = "external", path = "Movies/Family", folder = "Family"),
                video(2, volume = "sdcard", path = "Movies/Family", folder = "Family")
            ),
            "Unknown folder"
        )

        assertEquals(2, groups.size)
        assertEquals(
            setOf(
                "folder:external:Movies/Family",
                "folder:sdcard:Movies/Family"
            ),
            groups.map(LocalMediaGroup::stableKey).toSet()
        )
    }

    @Test
    fun `duplicate folder names show parent but preserve underlying folder identities`() {
        val videos = listOf(
            video(1, path = "Movies/Family", folder = "Family"),
            video(2, path = "Downloads/Family", folder = "Family"),
            video(3, path = "Pictures/Holiday", folder = "Holiday")
        )
        val groups = LocalMediaVideoIndex.folders(videos, "Unknown folder")
        assertEquals(
            setOf("Family (Movies)", "Family (Downloads)", "Holiday"),
            groups.map(LocalMediaGroup::title).toSet()
        )
        assertEquals(
            setOf("folder:external:Movies/Family", "folder:external:Downloads/Family", "folder:external:Pictures/Holiday"),
            groups.map(LocalMediaGroup::stableKey).toSet()
        )
        val plain = LocalMediaVideoIndex.folders(videos, "Unknown folder", showParentForDuplicates = false)
        assertEquals(2, plain.count { it.title == "Family" })
    }

    @Test
    fun `identical parents are qualified by storage location`() {
        val groups = LocalMediaVideoIndex.folders(
            listOf(
                video(1, volume = "external", path = "Movies/Family", folder = "Family"),
                video(2, volume = "sdcard", path = "Movies/Family", folder = "Family")
            ),
            "Unknown folder"
        )
        assertEquals(2, groups.map(LocalMediaGroup::title).toSet().size)
        assertEquals(true, groups.all { it.title.startsWith("Family (") })
    }

    @Test
    fun `mixed media can share a folder group`() {
        val groups = LocalMediaVideoIndex.folders(
            listOf(
                video(1, title = "Movie"),
                video(2, title = "Song").copy(
                    contentUri = "content://audio/2",
                    mimeType = "audio/mpeg",
                    isVideo = false
                )
            ),
            "Unknown folder",
            LocalMediaGroupKind.MEDIA_FOLDER
        )

        assertEquals(1, groups.size)
        assertEquals(LocalMediaGroupKind.MEDIA_FOLDER, groups.single().kind)
        assertEquals(setOf(false, true), groups.single().items.map(LocalMediaItem::isVideo).toSet())
    }

    @Test
    fun `videos inside folders are sorted by title`() {
        val groups = LocalMediaVideoIndex.folders(
            listOf(video(1, title = "Zulu"), video(2, title = "Alpha")),
            "Unknown folder"
        )

        assertEquals(listOf(2L, 1L), groups.single().items.map(LocalMediaItem::mediaStoreId))
    }

    private fun video(
        id: Long,
        title: String = "Video $id",
        volume: String = "external",
        path: String = "Movies",
        folder: String = "Movies"
    ) = LocalMediaItem(
        mediaStoreId = id,
        contentUri = "content://video/$id",
        title = title,
        artist = "",
        album = "",
        folder = folder,
        mimeType = "video/mp4",
        durationSeconds = 60,
        addedAtSeconds = id,
        isVideo = true,
        relativePath = path,
        volumeName = volume
    )
}
