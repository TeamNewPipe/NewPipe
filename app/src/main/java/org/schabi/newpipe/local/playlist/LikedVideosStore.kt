/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.content.SharedPreferences
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

/** Uses an ordinary local playlist; full backups retain both its database rows and profile link. */
class LikedVideosStore(
    private val database: AppDatabase,
    private val preferences: SharedPreferences,
    private val profileId: String,
    private val playlistName: String
) {
    fun observeLiked(serviceId: Int, url: String): Flowable<Boolean> = database.playlistDAO()
        .getAllForProfile(profileId)
        .switchMap { playlists ->
            val id = targetId()
            if (playlists.none { it.uid == id }) {
                Flowable.just(false)
            } else {
                database.playlistStreamDAO().observeQuickAddStreams(profileId, id)
                    .map { streams -> streams.any { it.serviceId == serviceId && it.url == url } }
            }
        }
        .distinctUntilChanged()

    fun getOrCreatePlaylist(): Long = database.runInTransaction<Long> { getOrCreate().uid }

    /** Idempotent writes and playlist creation share the Room transaction lock across rapid taps. */
    fun setLiked(stream: StreamEntity, liked: Boolean): Boolean = database.runInTransaction<Boolean> {
        val playlists = database.playlistDAO()
        val playlist = playlists.getPlaylistDirectForProfile(profileId, targetId())
            ?: if (liked) getOrCreate() else return@runInTransaction false
        val joins = database.playlistStreamDAO()
        val entries = joins.getQuickAddEntries(playlist.uid)
        val existing = database.streamDAO().getStreamDirect(stream.serviceId, stream.url)
        val matches = entries.filter { it.streamUid == existing?.uid }
        if (liked) {
            if (matches.isNotEmpty()) return@runInTransaction true
            val streamId = existing?.uid ?: database.streamDAO().upsert(stream.copy(uid = 0))
            joins.insert(PlaylistStreamEntity(playlist.uid, streamId, (entries.maxOfOrNull { it.index } ?: -1) + 1))
            if (!playlist.isThumbnailPermanent && playlist.thumbnailStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                playlist.thumbnailStreamId = streamId
                playlists.updateForProfile(profileId, playlist)
            }
        } else {
            matches.forEach(joins::delete)
            if (!playlist.isThumbnailPermanent && matches.any { it.streamUid == playlist.thumbnailStreamId }) {
                playlist.thumbnailStreamId = entries.firstOrNull { it !in matches }?.streamUid ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
                playlists.updateForProfile(profileId, playlist)
            }
        }
        liked
    }

    private fun targetId(): Long = (preferences.all[preferenceKey(profileId)] as? Number)?.toLong()?.takeIf { it > 0 } ?: -1L

    private fun getOrCreate(): PlaylistEntity {
        val playlists = database.playlistDAO()
        playlists.getPlaylistDirectForProfile(profileId, targetId())?.let { return it }
        val playlist = PlaylistEntity(
            name = playlistName,
            isThumbnailPermanent = false,
            thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
            displayIndex = -1,
            profileId = profileId
        )
        playlist.uid = playlists.insert(playlist)
        // A failed preference write rolls back creation. A stale link is never trusted without
        // checking the profile's playlist table, including after deletion or backup restoration.
        check(preferences.edit().putLong(preferenceKey(profileId), playlist.uid).commit()) {
            "Could not save the liked videos playlist link"
        }
        return playlist
    }

    companion object {
        fun preferenceKey(profileId: String): String = "liked_videos_playlist_$profileId"
    }
}
