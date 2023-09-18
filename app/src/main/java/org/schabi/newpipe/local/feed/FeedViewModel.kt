package org.schabi.newpipe.local.feed

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.functions.Function6
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ProgressEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.SuccessResultEvent
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class FeedViewModel(
    private val application: Application,
    groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    initialShowPlayedItems: Boolean,
    initialShowPartiallyPlayedItems: Boolean,
    initialShowFutureItems: Boolean
) : ViewModel() {
    private val feedDatabaseManager = FeedDatabaseManager(application)

    private val showPlayedItems = BehaviorProcessor.create<Boolean>()
    private val showPlayedItemsFlowable = showPlayedItems
        .startWithItem(initialShowPlayedItems)
        .distinctUntilChanged()

    private val showPartiallyPlayedItems = BehaviorProcessor.create<Boolean>()
    private val showPartiallyPlayedItemsFlowable = showPartiallyPlayedItems
        .startWithItem(initialShowPartiallyPlayedItems)
        .distinctUntilChanged()

    private val showFutureItems = BehaviorProcessor.create<Boolean>()
    private val showFutureItemsFlowable = showFutureItems
        .startWithItem(initialShowFutureItems)
        .distinctUntilChanged()

    private val mutableStateLiveData = MutableLiveData<FeedState>()
    val stateLiveData: LiveData<FeedState> = mutableStateLiveData

    private var combineDisposable = Flowable
        .combineLatest(
            FeedEventManager.events(),
            showPlayedItemsFlowable,
            showPartiallyPlayedItemsFlowable,
            showFutureItemsFlowable,
            feedDatabaseManager.notLoadedCount(groupId),
            feedDatabaseManager.oldestSubscriptionUpdate(groupId),

            Function6 { t1: FeedEventManager.Event, t2: Boolean, t3: Boolean, t4: Boolean,
                t5: Long, t6: List<OffsetDateTime> ->
                return@Function6 CombineResultEventHolder(t1, t2, t3, t4, t5, t6.firstOrNull())
            }
        )
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .map { (event, showPlayedItems, showPartiallyPlayedItems, showFutureItems, notLoadedCount, oldestUpdate) ->
            val streamItems = if (event is SuccessResultEvent || event is IdleEvent)
                feedDatabaseManager
                    .getStreams(groupId, showPlayedItems, showPartiallyPlayedItems, showFutureItems)
                    .blockingGet(arrayListOf())
            else
                arrayListOf()

            CombineResultDataHolder(event, streamItems, notLoadedCount, oldestUpdate)
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { (event, listFromDB, notLoadedCount, oldestUpdate) ->
            mutableStateLiveData.postValue(
                when (event) {
                    is IdleEvent -> FeedState.LoadedState(listFromDB.map { e -> StreamItem(e) }, oldestUpdate, notLoadedCount, listOf())
                    is ProgressEvent -> FeedState.ProgressState(event.currentProgress, event.maxProgress, event.progressMessage)
                    is SuccessResultEvent -> FeedState.LoadedState(listFromDB.map { e -> StreamItem(e) }, oldestUpdate, notLoadedCount, event.itemsErrors)
                    is ErrorResultEvent -> FeedState.ErrorState(event.error)
                }
            )

            if (event is ErrorResultEvent || event is SuccessResultEvent) {
                FeedEventManager.reset()
            }
        }

    override fun onCleared() {
        super.onCleared()
        combineDisposable.dispose()
    }

    private data class CombineResultEventHolder(
        val t1: FeedEventManager.Event,
        val t2: Boolean,
        val t3: Boolean,
        val t4: Boolean,
        val t5: Long,
        val t6: OffsetDateTime?
    )

    private data class CombineResultDataHolder(
        val t1: FeedEventManager.Event,
        val t2: List<StreamWithState>,
        val t3: Long,
        val t4: OffsetDateTime?
    )

    fun setSaveShowPlayedItems(showPlayedItems: Boolean) {
        this.showPlayedItems.onNext(showPlayedItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            this.putBoolean(application.getString(R.string.feed_show_watched_items_key), showPlayedItems)
            this.apply()
        }
    }

    fun getShowPlayedItemsFromPreferences() = getShowPlayedItemsFromPreferences(application)

    fun setSaveShowPartiallyPlayedItems(showPartiallyPlayedItems: Boolean) {
        this.showPartiallyPlayedItems.onNext(showPartiallyPlayedItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            this.putBoolean(application.getString(R.string.feed_show_partially_watched_items_key), showPartiallyPlayedItems)
            this.apply()
        }
    }

    fun getShowPartiallyPlayedItemsFromPreferences() = getShowPartiallyPlayedItemsFromPreferences(application)

    fun setSaveShowFutureItems(showFutureItems: Boolean) {
        this.showFutureItems.onNext(showFutureItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            this.putBoolean(application.getString(R.string.feed_show_future_items_key), showFutureItems)
            this.apply()
        }
    }

    fun getShowFutureItemsFromPreferences() = getShowFutureItemsFromPreferences(application)

    companion object {
        private fun getShowPlayedItemsFromPreferences(context: Context) =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.feed_show_watched_items_key), true)

        private fun getShowPartiallyPlayedItemsFromPreferences(context: Context) =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.feed_show_partially_watched_items_key), true)

        private fun getShowFutureItemsFromPreferences(context: Context) =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.feed_show_future_items_key), true)

        fun getFactory(context: Context, groupId: Long) = viewModelFactory {
            initializer {
                FeedViewModel(
                    App.getApp(),
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
