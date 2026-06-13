package org.schabi.newpipe.local.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.processors.PublishProcessor

/**
 * Activity-scoped ViewModel used as a message bus between
 * [VideoDetailFragment][org.schabi.newpipe.fragments.detail.VideoDetailFragment]
 * and [FeedFragment].
 *
 * Two trigger points post here:
 * 1. `VideoDetailFragment.handleResult` — stream info (view count) written to DB.
 * 2. `HistoryRecordManager.saveStreamState` — watch progress written to DB via [globalProgressBus].
 *
 * [FeedFragment] observes [updatedStream] and re-queries only the affected item from the DB.
 */
class StreamUpdateViewModel : ViewModel() {

    private val _updatedStream = MutableLiveData<Pair<Int, String>>()

    /** Emits (serviceId, url) whenever a stream's DB record (view count or progress) is updated. */
    val updatedStream: LiveData<Pair<Int, String>> = _updatedStream

    /** Called by VideoDetailFragment after the stream info (including view count) is stored. */
    fun notifyStreamInfoUpdated(serviceId: Int, url: String) {
        _updatedStream.postValue(Pair(serviceId, url))
    }

    companion object {
        /**
         * Process-wide bus used by [org.schabi.newpipe.local.history.HistoryRecordManager] (which
         * has no Activity context) to publish progress-save events.
         * [FeedFragment] subscribes to this bus directly in [FeedFragment.onViewCreated].
         */
        @JvmStatic
        val globalProgressBus: PublishProcessor<Pair<Int, String>> = PublishProcessor.create()

        /**
         * Called by [org.schabi.newpipe.local.history.HistoryRecordManager] every time it saves playback progress.
         * Safe to call from any thread.
         */
        @JvmStatic
        fun postProgressUpdate(serviceId: Int, url: String) {
            globalProgressBus.onNext(Pair(serviceId, url))
        }
    }
}
