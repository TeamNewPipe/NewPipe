package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class RemotePlaylistManager(private val database: AppDatabase) {
    private val playlistRemoteTable = database.playlistRemoteDAO()

    val playlists: Flowable<MutableList<PlaylistRemoteEntity>>
        get() = playlistRemoteTable.playlists.subscribeOn(Schedulers.io())

    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity> {
        return playlistRemoteTable.getPlaylist(playlistId).subscribeOn(Schedulers.io())
    }

    fun getPlaylist(info: PlaylistInfo): Flowable<MutableList<PlaylistRemoteEntity>> {
        return playlistRemoteTable.getPlaylist(info.serviceId.toLong(), info.url)
            .subscribeOn(Schedulers.io())
    }

    fun deletePlaylist(playlistId: Long): Single<Int> {
        return Single.fromCallable { playlistRemoteTable.deletePlaylist(playlistId) }
            .subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(
        updateItems: List<PlaylistRemoteEntity>,
        deletedItems: List<Long>
    ): Completable {
        return Completable.fromRunnable {
            database.runInTransaction {
                for (uid in deletedItems) {
                    playlistRemoteTable.deletePlaylist(uid)
                }
                for (item in updateItems) {
                    playlistRemoteTable.upsert(item)
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun onBookmark(playlistInfo: PlaylistInfo): Single<Long> {
        return Single.fromCallable {
            val playlist = PlaylistRemoteEntity(playlistInfo)
            playlistRemoteTable.upsert(playlist)
        }.subscribeOn(Schedulers.io())
    }

    fun onUpdate(playlistId: Long, playlistInfo: PlaylistInfo): Single<Int> {
        return Single.fromCallable {
            val playlist = PlaylistRemoteEntity(playlistInfo)
            playlist.uid = playlistId
            playlistRemoteTable.update(playlist)
        }.subscribeOn(Schedulers.io())
    }
}
