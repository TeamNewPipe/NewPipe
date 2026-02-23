/*
 * SPDX-FileCopyrightText: 2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.playlist.PlayListShareMode.JUST_URLS
import org.schabi.newpipe.local.playlist.PlayListShareMode.YOUTUBE_TEMP_PLAYLIST

class ExportPlaylistTest {

    @Test
    fun exportAsYouTubeTempPlaylist() {
        val playlist = asPlaylist(
            "https://www.youtube.com/watch?v=10000000000",
            "https://soundcloud.com/cautious-clayofficial/cold-war-2", // non-Youtube URLs should be ignored
            "https://www.youtube.com/watch?v=20000000000",
            "https://www.youtube.com/watch?v=30000000000"
        )

        val url = export(YOUTUBE_TEMP_PLAYLIST, playlist, mock(Context::class.java))

        assertEquals(
            "https://www.youtube.com/watch_videos?video_ids=" +
                "10000000000," +
                "20000000000," +
                "30000000000",
            url
        )
    }

    @Test
    fun exportMoreThan50Items() {
        /*
         * Playlist has more than 50 items => take the last 50
         * (YouTube limitation)
         */

        val playlist = asPlaylist(
            (10..70).map { id -> "https://www.youtube.com/watch?v=aaaaaaaaa$id" } // YouTube video IDs are 11 characters long
        )

        val url = export(YOUTUBE_TEMP_PLAYLIST, playlist, mock(Context::class.java))

        val videoIDs = (21..70).map { id -> "aaaaaaaaa$id" }.joinToString(",")

        assertEquals(
            "https://www.youtube.com/watch_videos?video_ids=$videoIDs",
            url
        )
    }

    @Test
    fun exportJustUrls() {
        val playlist = asPlaylist(
            "https://www.youtube.com/watch?v=10000000000",
            "https://www.youtube.com/watch?v=20000000000",
            "https://www.youtube.com/watch?v=30000000000"
        )

        val exported = export(JUST_URLS, playlist, mock(Context::class.java))

        assertEquals(
            """
            https://www.youtube.com/watch?v=10000000000
            https://www.youtube.com/watch?v=20000000000
            https://www.youtube.com/watch?v=30000000000
            """.trimIndent(),
            exported
        )
    }
}

fun asPlaylist(vararg urls: String): List<PlaylistStreamEntry> {
    return asPlaylist(listOf(*urls))
}

fun asPlaylist(urls: List<String>): List<PlaylistStreamEntry> {
    return urls.map { newPlaylistStreamEntry(it) }
}

fun newPlaylistStreamEntry(url: String): PlaylistStreamEntry {
    return PlaylistStreamEntry(newStreamEntity(url), 0, 0, 0)
}

fun newStreamEntity(url: String): StreamEntity {
    return StreamEntity(
        0,
        1,
        url,
        "Title",
        StreamType.VIDEO_STREAM,
        100,
        "Uploader"
    )
}
