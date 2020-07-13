package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<List<Group>>()
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<List<Group>> = mutableFeedGroupsLiveData

    private var feedGroupItemsDisposable = feedDatabaseManager.groups()
            .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
            .map { it.map(::FeedGroupCardItem) }
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

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }
}
