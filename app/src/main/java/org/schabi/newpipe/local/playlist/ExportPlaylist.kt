package org.schabi.newpipe.local.playlist

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.local.playlist.PlayListShareMode.JUST_URLS
import org.schabi.newpipe.local.playlist.PlayListShareMode.WITH_TITLES
import org.schabi.newpipe.local.playlist.PlayListShareMode.YOUTUBE_TEMP_PLAYLIST
import java.util.Objects.nonNull

fun export(
    shareMode: PlayListShareMode,
    playlist: List<PlaylistStreamEntry>,
    context: Context
): String {
    return when (shareMode) {
        WITH_TITLES -> exportWithTitles(playlist, context)
        JUST_URLS -> exportJustUrls(playlist)
        YOUTUBE_TEMP_PLAYLIST -> exportAsYoutubeTempPlaylist(playlist)
    }
}

fun exportWithTitles(
    playlist: List<PlaylistStreamEntry>,
    context: Context
): String {

    return playlist.asSequence()
        .map { it.streamEntity }
        .map { entity ->
            context.getString(
                R.string.video_details_list_item,
                entity.title,
                entity.url
            )
        }
        .joinToString(separator = "\n")
}

fun exportJustUrls(playlist: List<PlaylistStreamEntry>): String {

    return playlist.asSequence()
        .map { it.streamEntity.url }
        .joinToString(separator = "\n")
}

fun exportAsYoutubeTempPlaylist(playlist: List<PlaylistStreamEntry>): String {

    val videoIDs = playlist.asReversed().asSequence()
        .map { it.streamEntity }
        .map { getYouTubeId(it.url) }
        .filter(::nonNull)
        .take(50)
        .toList()
        .asReversed()
        .joinToString(separator = ",")

    return "http://www.youtube.com/watch_videos?video_ids=$videoIDs"
}

/**
 * Gets the video id from a YouTube URL.
 *
 * @param url YouTube URL
 * @return the video id
 */
fun getYouTubeId(url: String): String? {
    val httpUrl = url.toHttpUrlOrNull()

    return httpUrl?.queryParameter("v")
}
