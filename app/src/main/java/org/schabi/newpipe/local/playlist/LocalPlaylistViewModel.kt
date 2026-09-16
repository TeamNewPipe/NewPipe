/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.app.Application
import android.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Collections
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.local.history.HistoryRecordManager

class LocalPlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(application))
    private val recordManager = HistoryRecordManager(application)
    private val disposables = CompositeDisposable()

    private val _workState = MutableLiveData<WorkState>(WorkState.Idle)
    val workState: LiveData<WorkState> = _workState

    sealed class WorkState {
        object Idle : WorkState()
        object Loading : WorkState()
        data class Error(val throwable: Throwable) : WorkState()
        object Success : WorkState()
    }

    /**
     * Removes watched streams from the playlist.
     * This operation is performed in the background and survives fragment destruction
     * as it is not disposed when the ViewModel is cleared (to fix #8888).
     */
    fun removeWatchedStreams(playlistId: Long, removePartiallyWatched: Boolean) {
        _workState.value = WorkState.Loading

        val historyIdsMaybe = recordManager.streamHistorySortedById
            .firstElement()
            .map { historyList ->
                historyList.map { it.streamId }
            }

        val streamsMaybe = playlistManager.getPlaylistStreams(playlistId)
            .firstElement()
            .zipWith(historyIdsMaybe) { playlist, historyStreamIds ->
                val itemsToKeep = mutableListOf<PlaylistStreamEntry>()
                val isThumbnailPermanent = playlistManager.getIsPlaylistThumbnailPermanent(playlistId)
                var thumbnailVideoRemoved = false

                val streamStates = recordManager.loadLocalStreamStateBatch(playlist).blockingGet()

                for (i in playlist.indices) {
                    val playlistItem = playlist[i]
                    val streamStateEntity = streamStates[i]
                    val indexInHistory = Collections.binarySearch(historyStreamIds, playlistItem.streamId)
                    val duration = playlistItem.toStreamInfoItem().duration

                    if (indexInHistory < 0 || streamStateEntity == null ||
                        (!removePartiallyWatched && !streamStateEntity.isFinished(duration))
                    ) {
                        itemsToKeep.add(playlistItem)
                    } else if (!isThumbnailPermanent && !thumbnailVideoRemoved &&
                        playlistManager.getPlaylistThumbnailStreamId(playlistId) ==
                        playlistItem.streamEntity.uid
                    ) {
                        thumbnailVideoRemoved = true
                    }
                }

                Pair(itemsToKeep, thumbnailVideoRemoved)
            }

        val disposable = streamsMaybe
            .subscribeOn(Schedulers.io())
            .flatMapCompletable { flow ->
                val itemsToKeep = flow.first
                val thumbnailVideoRemoved = flow.second
                val streamIds = itemsToKeep.map { it.streamId }

                playlistManager.updateJoin(playlistId, streamIds)
                    .andThen(
                        Completable.fromAction {
                            if (thumbnailVideoRemoved) {
                                updateThumbnailUrl(playlistId, itemsToKeep)
                            }
                        }
                    )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { _workState.value = WorkState.Success },
                { _workState.value = WorkState.Error(it) }
            )

        disposables.add(disposable)
    }

    /**
     * Removes duplicate streams from the playlist.
     */
    fun removeDuplicates(playlistId: Long) {
        _workState.value = WorkState.Loading

        val disposable = playlistManager.getDistinctPlaylistStreams(playlistId)
            .firstElement()
            .subscribeOn(Schedulers.io())
            .flatMapCompletable { itemsToKeep ->
                val streamIds = itemsToKeep.map { it.streamId }
                playlistManager.updateJoin(playlistId, streamIds)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { _workState.value = WorkState.Success },
                { _workState.value = WorkState.Error(it) }
            )

        disposables.add(disposable)
    }

    private fun updateThumbnailUrl(playlistId: Long, itemsToKeep: List<PlaylistStreamEntry>) {
        if (playlistManager.getIsPlaylistThumbnailPermanent(playlistId)) {
            return
        }

        val thumbnailStreamId = if (itemsToKeep.isNotEmpty()) {
            itemsToKeep[0].streamEntity.uid
        } else {
            PlaylistEntity.DEFAULT_THUMBNAIL_ID
        }

        playlistManager.changePlaylistThumbnail(playlistId, thumbnailStreamId, false).blockingGet()
    }

    override fun onCleared() {
        super.onCleared()
        // We deliberately do not clear disposables here to allow pending
        // database mutations to finish even if the user leaves the screen.
    }
}
