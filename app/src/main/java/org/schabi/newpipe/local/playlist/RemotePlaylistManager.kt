package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import java.util.concurrent.Callable

class RemotePlaylistManager(private val database: AppDatabase) {
    private val playlistRemoteTable: PlaylistRemoteDAO?

    init {
        playlistRemoteTable = database.playlistRemoteDAO()
    }

    val playlists: Flowable<List<PlaylistRemoteEntity?>?>
        get() {
            return playlistRemoteTable!!.getPlaylists().subscribeOn(Schedulers.io())
        }

    fun getPlaylist(info: PlaylistInfo): Flowable<List<PlaylistRemoteEntity?>?> {
        return playlistRemoteTable!!.getPlaylist(info.getServiceId().toLong(), info.getUrl())
                .subscribeOn(Schedulers.io())
    }

    fun deletePlaylist(playlistId: Long): Single<Int?> {
        return Single.fromCallable(Callable({ playlistRemoteTable!!.deletePlaylist(playlistId) }))
                .subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(updateItems: List<PlaylistRemoteEntity>,
                        deletedItems: List<Long>): Completable {
        return Completable.fromRunnable(Runnable({
            database.runInTransaction(Runnable({
                for (uid: Long in deletedItems) {
                    playlistRemoteTable!!.deletePlaylist(uid)
                }
                for (item: PlaylistRemoteEntity in updateItems) {
                    playlistRemoteTable!!.upsert(item)
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun onBookmark(playlistInfo: PlaylistInfo): Single<Long?> {
        return Single.fromCallable(Callable({
            val playlist: PlaylistRemoteEntity = PlaylistRemoteEntity(playlistInfo)
            playlistRemoteTable!!.upsert(playlist)
        })).subscribeOn(Schedulers.io())
    }

    fun onUpdate(playlistId: Long, playlistInfo: PlaylistInfo): Single<Int?> {
        return Single.fromCallable(Callable({
            val playlist: PlaylistRemoteEntity = PlaylistRemoteEntity(playlistInfo)
            playlist.setUid(playlistId)
            playlistRemoteTable!!.update(playlist)
        })).subscribeOn(Schedulers.io())
    }
}
