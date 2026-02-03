package org.schabi.newpipe.player.playqueue

import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.App
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

class LocalPlaylistPlayQueue(info: PlaylistMetadataEntry) : PlayQueue(0, listOf()) {
    private val playlistId: Long = info.uid
    private var fetchDisposable: Disposable? = null
    override var isComplete: Boolean = false
        private set

    override fun fetch() {
        if (isComplete) {
            return
        }
        isComplete = true

        fetchDisposable = LocalPlaylistManager(NewPipeDatabase.getInstance(App.instance))
            .getPlaylistStreams(playlistId)
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { streamEntries ->
                    append(streamEntries.map { PlayQueueItem(it.toStreamInfoItem()) })
                },
                { e ->
                    Log.e(TAG, "Error fetching local playlist", e)
                    notifyChange()
                }
            )
    }

    override fun dispose() {
        super.dispose()
        fetchDisposable?.dispose()
        fetchDisposable = null
    }

    companion object {
        private val TAG: String = LocalPlaylistPlayQueue::class.java.getSimpleName()
    }
}
