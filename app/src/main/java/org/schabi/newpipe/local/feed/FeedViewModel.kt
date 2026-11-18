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
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function4
import io.reactivex.rxjava3.functions.Function6
import io.reactivex.rxjava3.functions.Function7
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.ktx.throttleLatestDropOverflow
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
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
    private val selectedGroup = BehaviorProcessor.createDefault(groupId)
    private val cachedFeedLimit = BehaviorProcessor.createDefault(FeedPagination.INITIAL_LIMIT)
    private var loadedFeedCount = 0

    fun selectGroup(groupId: Long) {
        if (groupId != selectedGroup.value) {
            loadedFeedCount = 0
            cachedFeedLimit.onNext(FeedPagination.INITIAL_LIMIT)
        }
        selectedGroup.onNext(groupId)
    }

    fun loadMoreCachedItems() {
        val limit = cachedFeedLimit.value ?: FeedPagination.INITIAL_LIMIT
        val next = FeedPagination.nextLimit(limit, loadedFeedCount)
        if (next > limit) cachedFeedLimit.onNext(next)
    }

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

    private val highlightBaselines = mutableMapOf<Pair<FeedScope, Long>, FeedHighlightBaseline>()
    private val refreshCompletions = mutableMapOf<Pair<FeedScope, Long>, FeedRefreshCompletions>()
    private var combineDisposable: Disposable? = null
    private val mutableStateLiveData = object : MutableLiveData<FeedState>() {
        override fun onActive() {
            combineDisposable = observeFeed()
        }

        override fun onInactive() {
            combineDisposable?.dispose()
            combineDisposable = null
        }
    }
    val stateLiveData: LiveData<FeedState> = mutableStateLiveData

    private val sortMode = BehaviorProcessor.createDefault(getSortMode())

    private fun observeFeed(): Disposable = Flowable.combineLatest(
        FeedScope.feedChanges(application),
        sortMode.distinctUntilChanged(),
        showPlayedItemsFlowable,
        showPartiallyPlayedItemsFlowable,
        showFutureItemsFlowable,
        selectedGroup.distinctUntilChanged(),
        cachedFeedLimit.distinctUntilChanged(),
        Function7 {
                scope: FeedScope,
                sort: FeedSortMode,
                played: Boolean,
                partial: Boolean,
                future: Boolean,
                group: Long,
                limit: Int
            ->
            FeedQuery(scope, sort, played, partial, future, group, limit)
        }
    )
        .switchMap { query ->
            // Observe committed content independently of progress. A progress tick must
            // neither query the database nor reconstruct all the StreamItems.
            val items = feedDatabaseManager.observeStreams(
                query.groupId,
                query.played,
                query.partial,
                query.future,
                query.scope,
                query.sortMode == FeedSortMode.DISCOVERY,
                limit = query.limit
            )
                .throttleLatestDropOverflow(FEED_SNAPSHOT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(Schedulers.computation())
                .map { streams -> streams.map(::StreamItem) }

            Flowable.combineLatest(
                FeedEventManager.events(query.scope),
                items,
                feedDatabaseManager.notLoadedCount(query.groupId, query.scope),
                feedDatabaseManager.oldestSubscriptionUpdate(query.groupId, query.scope),
                Function4 { event: FeedEventManager.Event, streamItems: List<StreamItem>, notLoadedCount: Long, oldestUpdate: List<OffsetDateTime?> ->
                    val highlightAfter = highlightBaselines.getOrPut(query.scope to query.groupId) { FeedHighlightBaseline() }
                        .update(oldestUpdate.firstOrNull(), event is ProgressEvent)
                    val state = when (event) {
                        is ErrorResultEvent -> FeedState.ErrorState(event.error)

                        else -> FeedState.LoadedState(
                            streamItems,
                            oldestUpdate.firstOrNull(),
                            notLoadedCount,
                            (event as? SuccessResultEvent)?.itemsErrors.orEmpty(),
                            (event as? ProgressEvent)?.let {
                                FeedState.ProgressState(
                                    it.currentProgress,
                                    it.maxProgress,
                                    it.progressMessage,
                                    it.updateDescription
                                )
                            },
                            highlightAfter
                        )
                    }
                    FeedResult(query.scope, event, state, query.groupId)
                }
            )
        }
        .throttleLatestDropOverflow(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { (scope, event, state, group) ->
                if (group != selectedGroup.value) return@subscribe
                if (state is FeedState.LoadedState) loadedFeedCount = state.items.size
                val completion = refreshCompletions.getOrPut(scope to group, ::FeedRefreshCompletions)
                    .update(event, (state as? FeedState.LoadedState)?.items)
                mutableStateLiveData.value = if (state is FeedState.LoadedState) {
                    state.copy(refreshCompletion = completion)
                } else {
                    state
                }
                if (event is ErrorResultEvent || event is SuccessResultEvent) {
                    FeedEventManager.reset(scope, event)
                }
            },
            { error -> mutableStateLiveData.value = FeedState.ErrorState(error) }
        )

    override fun onCleared() {
        super.onCleared()
        combineDisposable?.dispose()
        combineDisposable = null
    }

    private data class FeedQuery(
        val scope: FeedScope,
        val sortMode: FeedSortMode,
        val played: Boolean,
        val partial: Boolean,
        val future: Boolean,
        val groupId: Long,
        val limit: Int
    )

    private data class FeedResult(
        val scope: FeedScope,
        val event: FeedEventManager.Event,
        val state: FeedState,
        val groupId: Long
    )

    fun getSortMode(): FeedSortMode {
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        val stored = preferences.getString(application.getString(R.string.feed_sort_mode_key), null)
        val selected = stored?.let { runCatching { FeedSortMode.valueOf(it) }.getOrNull() }
            ?: if (
                preferences.getBoolean(
                    application.getString(R.string.feed_sort_by_discovery_key),
                    false
                )
            ) {
                FeedSortMode.DISCOVERY
            } else {
                FeedSortMode.PUBLICATION
            }
        return if (selected == FeedSortMode.RELEVANCE &&
            !org.schabi.newpipe.recommendations.LocalRecommendations.enabled(application)
        ) {
            FeedSortMode.PUBLICATION
        } else {
            selected
        }
    }

    fun setSortMode(mode: FeedSortMode) {
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putString(application.getString(R.string.feed_sort_mode_key), mode.name)
            putBoolean(
                application.getString(R.string.feed_sort_by_discovery_key),
                mode == FeedSortMode.DISCOVERY
            )
        }
        sortMode.onNext(mode)
    }

    fun getSortByDiscovery(): Boolean = getSortMode() == FeedSortMode.DISCOVERY

    fun setSortByDiscovery(enabled: Boolean) {
        setSortMode(if (enabled) FeedSortMode.DISCOVERY else FeedSortMode.PUBLICATION)
    }

    fun setSaveShowPlayedItems(showPlayedItems: Boolean) {
        this.showPlayedItems.onNext(showPlayedItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_watched_items_key), showPlayedItems)
        }
    }

    fun getShowPlayedItemsFromPreferences() = getShowPlayedItemsFromPreferences(application)

    fun setSaveShowPartiallyPlayedItems(showPartiallyPlayedItems: Boolean) {
        this.showPartiallyPlayedItems.onNext(showPartiallyPlayedItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_partially_watched_items_key), showPartiallyPlayedItems)
        }
    }

    fun getShowPartiallyPlayedItemsFromPreferences() = getShowPartiallyPlayedItemsFromPreferences(application)

    fun setSaveShowFutureItems(showFutureItems: Boolean) {
        this.showFutureItems.onNext(showFutureItems)
        PreferenceManager.getDefaultSharedPreferences(application).edit {
            putBoolean(application.getString(R.string.feed_show_future_items_key), showFutureItems)
        }
    }

    fun getShowFutureItemsFromPreferences() = getShowFutureItemsFromPreferences(application)

    companion object {
        private const val FEED_SNAPSHOT_INTERVAL_MILLIS = 500L

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
                    // The chips apply visibility defaults after loading. Explicit selections
                    // must be able to reveal items hidden by those defaults.
                    true,
                    true,
                    true
                )
            }
        }
    }
}
