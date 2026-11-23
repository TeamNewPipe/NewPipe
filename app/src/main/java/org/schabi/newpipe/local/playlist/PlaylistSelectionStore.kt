/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.local.ListSeparators

/** Applies a selected edit only while the complete persisted playlist still matches its source. */
class PlaylistSelectionStore(private val database: AppDatabase, private val activeProfileId: () -> String) {
    fun replace(
        profileId: String,
        playlistId: Long,
        expectedStreamIds: List<Long>,
        replacementStreamIds: List<Long>,
        afterCommit: () -> Unit = {}
    ): Single<Boolean> {
        val expected = expectedStreamIds.toList()
        val replacement = replacementStreamIds.toList()
        validateReplacement(expected, replacement)
        return Single.fromCallable {
            val accepted = replaceNow(profileId, playlistId, expected, replacement)
            // Complete captured, profile-scoped presentation updates even if the view was destroyed.
            if (accepted) afterCommit()
            accepted
        }.subscribeOn(Schedulers.io())
    }

    fun replaceWithHeadings(
        profileId: String,
        playlistId: Long,
        savedOrder: List<PlaylistStreamEntry>,
        replacement: List<PlaylistStreamEntry>,
        separators: ListSeparators,
        expectedHeadings: Map<Int, String>,
        replacementHeadings: Map<Int, String>
    ): Single<Boolean> {
        val source = savedOrder.toList()
        val expected = source.map { it.streamId }
        val result = replacement.map { it.streamId }
        val oldHeadings = expectedHeadings.toMap()
        val newHeadings = replacementHeadings.toMap()
        validateReplacement(expected, result)
        return Single.fromCallable {
            separators.commitSelection(source, oldHeadings, newHeadings) {
                replaceNow(profileId, playlistId, expected, result)
            }
        }.subscribeOn(Schedulers.io())
    }

    /** Move exact occurrences and append them in the selected display order in one transaction. */
    fun moveToPlaylist(
        profileId: String,
        playlistId: Long,
        savedOrder: List<PlaylistStreamEntry>,
        selectedIndices: List<Int>,
        destination: PlaylistTransferDestination,
        separators: ListSeparators,
        expectedHeadings: Map<Int, String>,
        replacementHeadings: Map<Int, String>
    ): Single<Boolean> {
        val source = savedOrder.toList()
        val selected = selectedIndices.toList()
        require(selected.isNotEmpty() && selected.distinct().size == selected.size)
        require(selected.all { it in source.indices })
        val expected = source.map { it.streamId }
        val moving = selected.map(expected::get)
        val selectedSet = selected.toSet()
        val remaining = expected.filterIndexed { index, _ -> index !in selectedSet }
        val oldHeadings = expectedHeadings.toMap()
        val newHeadings = replacementHeadings.toMap()
        val target = when (destination) {
            is PlaylistTransferDestination.Existing -> destination
            is PlaylistTransferDestination.Create -> destination.copy(name = destination.name.trim()).also { require(it.name.isNotEmpty()) }
        }
        return Single.fromCallable {
            separators.commitSelection(source, oldHeadings, newHeadings) {
                database.runInTransaction(
                    Callable {
                        if (activeProfileId() != profileId) return@Callable false
                        val playlists = database.playlistDAO()
                        val streams = database.playlistStreamDAO()
                        val from = playlists.getPlaylistDirectForProfile(profileId, playlistId) ?: return@Callable false
                        if (streams.getQuickAddEntries(playlistId).map { it.streamUid } != expected) return@Callable false
                        val to = when (target) {
                            is PlaylistTransferDestination.Existing -> {
                                if (target.playlistId == playlistId) return@Callable false
                                playlists.getPlaylistDirectForProfile(profileId, target.playlistId) ?: return@Callable false
                            }

                            is PlaylistTransferDestination.Create -> PlaylistEntity(
                                name = target.name,
                                isThumbnailPermanent = false,
                                thumbnailStreamId = moving.first(),
                                displayIndex = -1,
                                profileId = profileId
                            )
                        }
                        val maximumIndex = if (target is PlaylistTransferDestination.Create) -1 else streams.getMaximumIndexDirect(to.uid)
                        val firstTargetStream = if (target is PlaylistTransferDestination.Create) null else streams.getFirstStreamIdDirect(to.uid)
                        if (maximumIndex.toLong() + moving.size > Int.MAX_VALUE) return@Callable false
                        if (target is PlaylistTransferDestination.Create) to.uid = playlists.insert(to)
                        // Any insertion failure rolls back both playlists, including a new target.
                        check(streams.insertAllForProfile(profileId, to.uid, moving.mapIndexed { index, id -> PlaylistStreamEntity(to.uid, id, maximumIndex + 1 + index) }).size == moving.size)
                        streams.deleteBatchForProfile(profileId, playlistId)
                        check(streams.insertAllForProfile(profileId, playlistId, remaining.mapIndexed { index, id -> PlaylistStreamEntity(playlistId, id, index) }).size == remaining.size)
                        if (!from.isThumbnailPermanent) {
                            from.thumbnailStreamId = remaining.firstOrNull() ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
                            playlists.updateForProfile(profileId, from)
                        }
                        if (!to.isThumbnailPermanent) {
                            to.thumbnailStreamId = firstTargetStream ?: moving.first()
                            playlists.updateForProfile(profileId, to)
                        }
                        true
                    }
                )
            }
        }.subscribeOn(Schedulers.io())
    }

    private fun replaceNow(profileId: String, playlistId: Long, expected: List<Long>, replacement: List<Long>): Boolean = database.runInTransaction(
        Callable {
            val streams = database.playlistStreamDAO()
            if (activeProfileId() != profileId || !streams.playlistBelongsToProfile(profileId, playlistId)) return@Callable false
            if (streams.getQuickAddEntries(playlistId).map { it.streamUid } != expected) return@Callable false
            val playlist = database.playlistDAO().getPlaylistDirectForProfile(profileId, playlistId) ?: return@Callable false
            streams.deleteBatchForProfile(profileId, playlistId)
            streams.insertAllForProfile(profileId, playlistId, replacement.mapIndexed { index, id -> PlaylistStreamEntity(playlistId, id, index) })
            if (!playlist.isThumbnailPermanent) {
                playlist.thumbnailStreamId = replacement.firstOrNull() ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
                database.playlistDAO().updateForProfile(profileId, playlist)
            }
            true
        }
    )

    private fun validateReplacement(expected: List<Long>, replacement: List<Long>) {
        val available = expected.groupingBy { it }.eachCount()
        require(replacement.groupingBy { it }.eachCount().all { (id, count) -> count <= (available[id] ?: 0) })
    }
}
