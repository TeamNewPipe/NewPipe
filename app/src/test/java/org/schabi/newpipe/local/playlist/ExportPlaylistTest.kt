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
import java.util.stream.Stream

class ExportPlaylistTest {

    @Test
    fun exportAsYouTubeTempPlaylist() {
        val playlist = asPlaylist(
            "https://www.youtube.com/watch?v=1",
            "https://soundcloud.com/cautious-clayofficial/cold-war-2", // non-Youtube URLs should be ignored
            "https://www.youtube.com/watch?v=2",
            "https://www.youtube.com/watch?v=3"
        )

        val url = export(YOUTUBE_TEMP_PLAYLIST, playlist, mock(Context::class.java))

        assertEquals("http://www.youtube.com/watch_videos?video_ids=1,2,3", url)
    }

    @Test
    fun exportMoreThan50Items() {
        /*
         * Playlist has more than 50 items => take the last 50
         * (YouTube limitation)
         */

        val ids = listOf(
            -1, 0,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50
        )

        val playlist = asPlaylist(
            ids.stream()
                .map { id: Int -> "https://www.youtube.com/watch?v=$id" }
        )

        val url = export(YOUTUBE_TEMP_PLAYLIST, playlist, mock(Context::class.java))

        assertEquals(
            "http://www.youtube.com/watch_videos?video_ids=" +
                "1,2,3,4,5,6,7,8,9,10," +
                "11,12,13,14,15,16,17,18,19,20," +
                "21,22,23,24,25,26,27,28,29,30," +
                "31,32,33,34,35,36,37,38,39,40," +
                "41,42,43,44,45,46,47,48,49,50",

            url
        )
    }

    @Test
    fun exportJustUrls() {
        val playlist = asPlaylist(
            "https://www.youtube.com/watch?v=1",
            "https://www.youtube.com/watch?v=2",
            "https://www.youtube.com/watch?v=3"
        )

        val exported = export(JUST_URLS, playlist, mock(Context::class.java))

        assertEquals(
            """
            https://www.youtube.com/watch?v=1
            https://www.youtube.com/watch?v=2
            https://www.youtube.com/watch?v=3
            """.trimIndent(),
            exported
        )
    }
}

fun asPlaylist(vararg urls: String): List<PlaylistStreamEntry> {
    return asPlaylist(Stream.of(*urls))
}

fun asPlaylist(urls: Stream<String>): List<PlaylistStreamEntry> {
    return urls
        .map { url: String -> newPlaylistStreamEntry(url) }
        .toList()
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
