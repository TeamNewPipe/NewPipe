package org.schabi.newpipe.local.playlist

import androidx.room.withTransaction
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.rxCompletable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

class RemotePlaylistManager(db: AppDatabase) {
    private val database = db
    private val playlistRemoteTable = db.playlistRemoteDAO()

    fun getPlaylists(): Flowable<List<PlaylistRemoteEntity>> {
        return playlistRemoteTable.getPlaylists().subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(
        updateItems: List<PlaylistRemoteEntity>,
        deletedItems: List<Long>,
    ) = rxCompletable(Dispatchers.IO) {
        database.withTransaction {
            for (uid in deletedItems) {
                playlistRemoteTable.deletePlaylist(uid)
            }
            for (item in updateItems) {
                playlistRemoteTable.upsert(item)
            }
        }
    }
}
