package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import java.util.concurrent.TimeUnit

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    val stateLiveData = MutableLiveData<SubscriptionState>()
    val feedGroupsLiveData = MutableLiveData<List<Group>>()

    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    private var feedGroupItemsDisposable = feedDatabaseManager.groups()
            .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
            .map { it.map(::FeedGroupCardItem) }
            .subscribeOn(Schedulers.io())
            .subscribe(
                    { feedGroupsLiveData.postValue(it) },
                    { stateLiveData.postValue(SubscriptionState.ErrorState(it)) }
            )

    private var stateItemsDisposable = subscriptionManager.subscriptions()
            .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
            .map { it.map { entity -> ChannelItem(entity.toChannelInfoItem(), entity.uid, ChannelItem.ItemVersion.MINI) } }
            .subscribeOn(Schedulers.io())
            .subscribe(
                    { stateLiveData.postValue(SubscriptionState.LoadedState(it)) },
                    { stateLiveData.postValue(SubscriptionState.ErrorState(it)) }
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
