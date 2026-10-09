package org.schabi.newpipe.local.subscription

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.HashSet
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    // true -> list view, false -> grid view
    private val listViewMode = BehaviorProcessor.createDefault(
        !shouldUseGridForSubscription(application)
    )
    private val listViewModeFlowable = listViewMode.distinctUntilChanged()

    private val selectedUrls = BehaviorProcessor.createDefault<Set<String>>(HashSet())
    private val isManagementMode = BehaviorProcessor.createDefault(false)

    private var currentSubscriptions: List<SubscriptionEntity> = emptyList()
    private var currentSubscriptionUrls: List<String> = emptyList()

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<Pair<List<Group>, Boolean>>()
    private val mutableManagementModeLiveData = MutableLiveData<Boolean>()

    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<Pair<List<Group>, Boolean>> = mutableFeedGroupsLiveData
    val managementModeLiveData: LiveData<Boolean> = mutableManagementModeLiveData

    private var feedGroupItemsDisposable = Flowable
        .combineLatest(
            feedDatabaseManager.groups(),
            listViewModeFlowable,
            ::Pair
        )
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { (feedGroups, listViewMode) ->
            Pair(
                feedGroups.map(if (listViewMode) ::FeedGroupCardItem else ::FeedGroupCardGridItem),
                listViewMode
            )
        }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableFeedGroupsLiveData.postValue(it) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    private var stateItemsDisposable = Flowable.combineLatest(
        subscriptionManager.subscriptions(),
        selectedUrls,
        isManagementMode,
        { subs, selected, management -> Triple(subs, selected, management) }
    )
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .doOnNext { (subscriptions, _, _) ->
            currentSubscriptions = subscriptions
            currentSubscriptionUrls = subscriptions.mapNotNull { it.url }
        }
        .map { (subscriptions, selected, _) ->
            subscriptions.map { entity ->
                ChannelItem(
                    entity.toChannelInfoItem(),
                    entity.uid,
                    ChannelItem.ItemVersion.MINI,
                    null,
                    selected.contains(entity.url)
                )
            }
        }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableStateLiveData.postValue(SubscriptionState.LoadedState(it)) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    private var managementModeDisposable = isManagementMode
        .subscribeOn(Schedulers.io())
        .subscribe { mutableManagementModeLiveData.postValue(it) }

    override fun onCleared() {
        super.onCleared()
        stateItemsDisposable.dispose()
        feedGroupItemsDisposable.dispose()
        managementModeDisposable.dispose()
    }

    fun setListViewMode(newListViewMode: Boolean) {
        listViewMode.onNext(newListViewMode)
    }

    fun getListViewMode(): Boolean {
        return listViewMode.value ?: true
    }

    fun toggleSelection(url: String) {
        val current = selectedUrls.value?.toMutableSet() ?: HashSet()
        if (current.contains(url)) {
            current.remove(url)
        } else {
            current.add(url)
        }
        selectedUrls.onNext(current)
    }

    fun selectAll() {
        val currentSelected = selectedUrls.value ?: emptySet()
        if (currentSelected.size >= currentSubscriptionUrls.size &&
            currentSelected.containsAll(currentSubscriptionUrls)
        ) {
            selectedUrls.onNext(emptySet())
        } else {
            selectedUrls.onNext(currentSubscriptionUrls.toSet())
        }
    }

    fun setManagementMode(enabled: Boolean) {
        isManagementMode.onNext(enabled)
        if (!enabled) {
            selectedUrls.onNext(HashSet())
        }
    }

    fun isManagementMode(): Boolean {
        return isManagementMode.value ?: false
    }

    fun getSelectedCount(): Int {
        return selectedUrls.value?.size ?: 0
    }

    fun unsubscribeSelected(): Completable {
        val selected = selectedUrls.value ?: return Completable.complete()
        val toDelete = currentSubscriptions.filter { selected.contains(it.url) }

        return if (toDelete.isEmpty()) {
            Completable.complete()
        } else {
            subscriptionManager.deleteSubscriptions(toDelete)
                .doOnComplete { setManagementMode(false) }
        }
    }

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }

    companion object {

        /**
         * Returns whether to use GridLayout mode for Subscription Fragment.
         *
         * ### Current mapping:
         *
         *  | ItemViewMode | ItemVersion | Span count |
         *  |---|---|---|
         *  | AUTO | MINI | 1 |
         *  | LIST | MINI | 1 |
         *  | CARD | GRID | > 1 (ThemeHelper defined) |
         *  | GRID | GRID | > 1 (ThemeHelper defined) |
         *
         *  @see [SubscriptionViewModel.shouldUseGridForSubscription] to modify Layout Manager
         */
        fun shouldUseGridForSubscription(context: Context): Boolean {
            val itemViewMode = getItemViewMode(context)
            return itemViewMode == ItemViewMode.GRID || itemViewMode == ItemViewMode.CARD
        }
    }
}
