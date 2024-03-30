package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.util.concurrent.Callable

class LocalPlaylistManager(private val database: AppDatabase) {
    private val streamTable: StreamDAO?
    private val playlistTable: PlaylistDAO?
    private val playlistStreamTable: PlaylistStreamDAO?

    init {
        streamTable = database.streamDAO()
        playlistTable = database.playlistDAO()
        playlistStreamTable = database.playlistStreamDAO()
    }

    fun createPlaylist(name: String?, streams: List<StreamEntity?>?): Maybe<List<Long>> {
        // Disallow creation of empty playlists
        if (streams!!.isEmpty()) {
            return Maybe.empty()
        }

        // Save to the database directly.
        // Make sure the new playlist is always on the top of bookmark.
        // The index will be reassigned to non-negative number in BookmarkFragment.
        return Maybe.fromCallable(Callable<List<Long>>({
            database.runInTransaction<List<Long>>(Callable({
                val streamIds: List<Long> = streamTable!!.upsertAll(streams)
                val newPlaylist: PlaylistEntity = PlaylistEntity(name, false,
                        streamIds.get(0), -1)
                insertJoinEntities(playlistTable!!.insert(newPlaylist),
                        streamIds, 0)
            })
            )
        })).subscribeOn(Schedulers.io())
    }

    fun appendToPlaylist(playlistId: Long,
                         streams: List<StreamEntity?>?): Maybe<List<Long?>> {
        return playlistStreamTable!!.getMaximumIndexOf(playlistId)
                .firstElement()
                .map(Function<Int?, List<Long?>>({ maxJoinIndex: Int? ->
                    database.runInTransaction<List<Long?>>(Callable({
                        val streamIds: List<Long> = streamTable!!.upsertAll(streams)
                        insertJoinEntities(playlistId, streamIds, maxJoinIndex!! + 1)
                    })
                    )
                })).subscribeOn(Schedulers.io())
    }

    private fun insertJoinEntities(playlistId: Long, streamIds: List<Long>,
                                   indexOffset: Int): List<Long?>? {
        val joinEntities: MutableList<PlaylistStreamEntity> = ArrayList(streamIds.size)
        for (index in streamIds.indices) {
            joinEntities.add(PlaylistStreamEntity(playlistId, streamIds.get(index),
                    index + indexOffset))
        }
        return playlistStreamTable!!.insertAll(joinEntities)
    }

    fun updateJoin(playlistId: Long, streamIds: List<Long>): Completable {
        val joinEntities: MutableList<PlaylistStreamEntity> = ArrayList(streamIds.size)
        for (i in streamIds.indices) {
            joinEntities.add(PlaylistStreamEntity(playlistId, streamIds.get(i), i))
        }
        return Completable.fromRunnable(Runnable({
            database.runInTransaction(Runnable({
                playlistStreamTable!!.deleteBatch(playlistId)
                playlistStreamTable.insertAll(joinEntities)
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(updateItems: List<PlaylistMetadataEntry>,
                        deletedItems: List<Long>): Completable {
        val items: MutableList<PlaylistEntity> = ArrayList(updateItems.size)
        for (item: PlaylistMetadataEntry in updateItems) {
            items.add(PlaylistEntity(item))
        }
        return Completable.fromRunnable(Runnable({
            database.runInTransaction(Runnable({
                for (uid: Long in deletedItems) {
                    playlistTable!!.deletePlaylist(uid)
                }
                for (item: PlaylistEntity in items) {
                    playlistTable!!.upsertPlaylist(item)
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun getDistinctPlaylistStreams(playlistId: Long): Flowable<List<PlaylistStreamEntry?>?> {
        return playlistStreamTable
                .getStreamsWithoutDuplicates(playlistId).subscribeOn(Schedulers.io())
    }

    /**
     * Get playlists with attached information about how many times the provided stream is already
     * contained in each playlist.
     *
     * @param streamUrl the stream url for which to check for duplicates
     * @return a list of [PlaylistDuplicatesEntry]
     */
    fun getPlaylistDuplicates(streamUrl: String?): Flowable<List<PlaylistDuplicatesEntry?>?> {
        return playlistStreamTable!!.getPlaylistDuplicatesMetadata(streamUrl)
                .subscribeOn(Schedulers.io())
    }

    val playlists: Flowable<List<PlaylistMetadataEntry?>?>
        get() {
            return playlistStreamTable!!.getPlaylistMetadata().subscribeOn(Schedulers.io())
        }

    fun getPlaylistStreams(playlistId: Long): Flowable<List<PlaylistStreamEntry?>?> {
        return playlistStreamTable!!.getOrderedStreamsOf(playlistId).subscribeOn(Schedulers.io())
    }

    fun renamePlaylist(playlistId: Long, name: String?): Maybe<Int?> {
        return modifyPlaylist(playlistId, name, THUMBNAIL_ID_LEAVE_UNCHANGED, false)
    }

    fun changePlaylistThumbnail(playlistId: Long,
                                thumbnailStreamId: Long,
                                isPermanent: Boolean): Maybe<Int?> {
        return modifyPlaylist(playlistId, null, thumbnailStreamId, isPermanent)
    }

    fun getPlaylistThumbnailStreamId(playlistId: Long): Long {
        return playlistTable!!.getPlaylist(playlistId).blockingFirst().get(0)!!.getThumbnailStreamId()
    }

    fun getIsPlaylistThumbnailPermanent(playlistId: Long): Boolean {
        return playlistTable!!.getPlaylist(playlistId).blockingFirst().get(0)
                .getIsThumbnailPermanent()
    }

    fun getAutomaticPlaylistThumbnailStreamId(playlistId: Long): Long {
        val streamId: Long = playlistStreamTable!!.getAutomaticThumbnailStreamId(playlistId)
                .blockingFirst()
        if (streamId < 0) {
            return PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID
        }
        return streamId
    }

    private fun modifyPlaylist(playlistId: Long,
                               name: String?,
                               thumbnailStreamId: Long,
                               isPermanent: Boolean): Maybe<Int?> {
        return playlistTable!!.getPlaylist(playlistId)
                .firstElement()
                .filter(Predicate<List<PlaylistEntity?>?>({ playlistEntities: List<PlaylistEntity?>? -> !playlistEntities!!.isEmpty() }))
                .map(Function<List<PlaylistEntity?>?, Int?>({ playlistEntities: List<PlaylistEntity>? ->
                    val playlist: PlaylistEntity = playlistEntities!!.get(0)
                    if (name != null) {
                        playlist.setName(name)
                    }
                    if (thumbnailStreamId != THUMBNAIL_ID_LEAVE_UNCHANGED) {
                        playlist.setThumbnailStreamId(thumbnailStreamId)
                        playlist.setIsThumbnailPermanent(isPermanent)
                    }
                    playlistTable.update(playlist)
                })).subscribeOn(Schedulers.io())
    }

    fun hasPlaylists(): Maybe<Boolean?> {
        return playlistTable!!.getCount()
                .firstElement()
                .map(Function<Long?, Boolean?>({ count: Long? -> count!! > 0 }))
                .subscribeOn(Schedulers.io())
    }

    companion object {
        private val THUMBNAIL_ID_LEAVE_UNCHANGED: Long = -2
    }
}
