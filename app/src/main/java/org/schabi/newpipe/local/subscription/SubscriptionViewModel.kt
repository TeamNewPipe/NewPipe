package org.schabi.newpipe.local.subscription

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode
import java.util.concurrent.TimeUnit

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    // true -> list view, false -> grid view
    private val listViewMode = BehaviorProcessor.createDefault(
        !shouldUseGridForSubscription(application)
    )
    private val listViewModeFlowable = listViewMode.distinctUntilChanged()

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<Pair<List<Group>, Boolean>>()
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<Pair<List<Group>, Boolean>> = mutableFeedGroupsLiveData

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

    private var stateItemsDisposable = subscriptionManager.subscriptions()
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { it.map { entity -> ChannelItem(entity.toChannelInfoItem(), entity.uid, ChannelItem.ItemVersion.MINI) } }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableStateLiveData.postValue(SubscriptionState.LoadedState(it)) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    override fun onCleared() {
        super.onCleared()
        stateItemsDisposable.dispose()
        feedGroupItemsDisposable.dispose()
    }

    fun setListViewMode(newListViewMode: Boolean) {
        listViewMode.onNext(newListViewMode)
    }

    fun getListViewMode(): Boolean {
        return listViewMode.value ?: true
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
