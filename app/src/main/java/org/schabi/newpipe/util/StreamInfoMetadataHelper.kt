/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.SongMetadata
import org.schabi.newpipe.extractor.stream.StreamInfo

class StreamInfoMetadataHelper(
    val streamInfo: StreamInfo
) {
    val songInfo: SongMetadata? = streamInfo.songMetadata

    val title: String
        get() {
            return if (songInfo?.title?.contentEquals(streamInfo.name) == true) {
                // YT Music is sometimes using uppercase chars in the description,
                // but the StreamInfo name is using the correct case, so we prefer that
                streamInfo.name
            } else if (songInfo?.title?.isBlank() == false) {
                songInfo.title
            } else {
                streamInfo.name
            }
        }

    fun getArtist(): String {
        if (songInfo?.artist?.contentEquals(streamInfo.uploaderName) == true) {
            // YT Music uses uppercase chars in the description, but the uploader name is using
            // the correct case, so we prefer the uploader name
            return streamInfo.uploaderName
        }
        return if (songInfo?.artist?.isBlank() == false) {
            songInfo.artist
        } else {
            streamInfo.uploaderName
        }
    }

    fun getPerformer(): List<String?> = songInfo?.performer ?: emptyList()

    fun getComposer(): String? = songInfo?.composer

    fun getGenre(): String? = if (songInfo?.genre?.isEmpty() == false) {
        songInfo.genre
    } else {
        streamInfo.category
    }

    fun getAlbum(): String? = songInfo?.album

    fun getTrackNumber(): Int? = if (songInfo?.track != SongMetadata.TRACK_UNKNOWN) songInfo?.track else null

    fun getDuration(): Long = songInfo?.duration?.seconds ?: streamInfo.duration

    fun getReleaseDate(): DateWrapper = songInfo?.releaseDate ?: streamInfo.uploadDate

    val recordLabel: String? = songInfo?.label

    val copyright: String? = songInfo?.copyright ?: streamInfo.licence

    val location: String? = songInfo?.location
}
