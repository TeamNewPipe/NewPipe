package org.schabi.newpipe.local.feed

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.preference.PreferenceManager
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT

class FeedViewModel(
    private val application: Application,
    groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    initialShowPlayedItems: Boolean,
    initialShowPartiallyPlayedItems: Boolean,
    initialShowFutureItems: Boolean
) : ViewModel() {
    private val feedDatabaseManager = FeedDatabaseManager(application)

    private val showPlayedItems = MutableStateFlow(initialShowPlayedItems)
    private val showPartiallyPlayedItems = MutableStateFlow(initialShowPartiallyPlayedItems)
    private val showFutureItems = MutableStateFlow(initialShowFutureItems)

    private val mutableStateLiveData = MutableLiveData<FeedState>()
    val stateLiveData: LiveData<FeedState> = mutableStateLiveData

    init {
        combine(
            FeedEventManager.events(),
            showPlayedItems,
            showPartiallyPlayedItems,
            showFutureItems,
            feedDatabaseManager.notLoadedCount(groupId),
            feedDatabaseManager.oldestSubscriptionUpdate(groupId)
        ) { array ->
            CombineResultEventHolder(
                array[0] as FeedEventManager.Event,
                array[1] as Boolean,
                array[2] as Boolean,
                array[3] as Boolean,
                array[4] as Long,
                (array[5] as List<OffsetDateTime?>).firstOrNull()
            )
        }
            .debounce(DEFAULT_THROTTLE_TIMEOUT)
            .onEach { holder ->
                val (event, showPlayed, showPartiallyPlayed, showFuture, notLoadedCount, oldestUpdate) = holder
                val streamItems = if (event is SuccessResultEvent || event is IdleEvent) {
                    feedDatabaseManager
                        .getStreams(groupId, showPlayed, showPartiallyPlayed, showFuture)
                } else {
                    emptyList()
                }

                val state = when (event) {
                    is IdleEvent -> FeedState.LoadedState(streamItems, oldestUpdate, notLoadedCount, listOf())
                    is ProgressEvent -> FeedState.ProgressState(event.currentProgress, event.maxProgress, event.progressMessage)
                    is SuccessResultEvent -> FeedState.LoadedState(streamItems, oldestUpdate, notLoadedCount, event.itemsErrors)
                    is ErrorResultEvent -> FeedState.ErrorState(event.error)
                }
                mutableStateLiveData.postValue(state)

                if (event is ErrorResultEvent || event is SuccessResultEvent) {
                    FeedEventManager.reset()
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    private data class CombineResultEventHolder(
        val t1: FeedEventManager.Event,
        val t2: Boolean,
        val t3: Boolean,
        val t4: Boolean,
        val t5: Long,
        val t6: OffsetDateTime?
    )

    fun setSaveShowPlayedItems(showPlayedItems: Boolean) {
        this.showPlayedItems.value = showPlayedItems
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_watched_items_key), showPlayedItems)
        }
    }

    fun getShowPlayedItemsFromPreferences() = getShowPlayedItemsFromPreferences(application)

    fun setSaveShowPartiallyPlayedItems(showPartiallyPlayedItems: Boolean) {
        this.showPartiallyPlayedItems.value = showPartiallyPlayedItems
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_partially_watched_items_key), showPartiallyPlayedItems)
        }
    }

    fun getShowPartiallyPlayedItemsFromPreferences() = getShowPartiallyPlayedItemsFromPreferences(application)

    fun setSaveShowFutureItems(showFutureItems: Boolean) {
        this.showFutureItems.value = showFutureItems
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_future_items_key), showFutureItems)
        }
    }

    fun getShowFutureItemsFromPreferences() = getShowFutureItemsFromPreferences(application)

    companion object {
        private fun getShowPlayedItemsFromPreferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.feed_show_watched_items_key), true)

        private fun getShowPartiallyPlayedItemsFromPreferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.feed_show_partially_watched_items_key), true)

        private fun getShowFutureItemsFromPreferences(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.feed_show_future_items_key), true)

        fun getFactory(context: Context, groupId: Long) = viewModelFactory {
            initializer {
                FeedViewModel(
                    App.instance,
                    groupId,
                    // Read initial value from preferences
                    getShowPlayedItemsFromPreferences(context.applicationContext),
                    getShowPartiallyPlayedItemsFromPreferences(context.applicationContext),
                    getShowFutureItemsFromPreferences(context.applicationContext)
                )
            }
        }
    }
}
