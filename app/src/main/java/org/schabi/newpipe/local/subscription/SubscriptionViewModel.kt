package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xwray.groupie.Group
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import java.util.concurrent.TimeUnit

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)
    private var subscriptionManager = SubscriptionManager(application)

    private val mutableStateLiveData = MutableLiveData<SubscriptionState>()
    private val mutableFeedGroupsLiveData = MutableLiveData<List<Group>>()
    private val mutableFeedGroupsVerticalLiveData = MutableLiveData<List<FeedGroupEntity>>()
    private val mutableEventLiveData = MutableLiveData<SubscriptionViewModel.Event>()
    val stateLiveData: LiveData<SubscriptionState> = mutableStateLiveData
    val feedGroupsLiveData: LiveData<List<Group>> = mutableFeedGroupsLiveData
    val feedGroupsVerticalLiveData: LiveData<List<FeedGroupEntity>> = mutableFeedGroupsVerticalLiveData
    val eventLiveData: LiveData<SubscriptionViewModel.Event> = mutableEventLiveData

    private var actionProcessingDisposable: Disposable? = null

    private var feedGroupItemsDisposable = feedDatabaseManager.groups()
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .map { it.map(::FeedGroupCardItem) }
        .subscribeOn(Schedulers.io())
        .subscribe(
            { mutableFeedGroupsLiveData.postValue(it) },
            { mutableStateLiveData.postValue(SubscriptionState.ErrorState(it)) }
        )

    private var feedGroupVerticalItemsDisposable = feedDatabaseManager.groups()
        .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
        .take(1)
        .subscribeOn(Schedulers.io())
        .subscribe(mutableFeedGroupsVerticalLiveData::postValue)

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
        feedGroupVerticalItemsDisposable.dispose()
    }

    sealed class SubscriptionState {
        data class LoadedState(val subscriptions: List<Group>) : SubscriptionState()
        data class ErrorState(val error: Throwable? = null) : SubscriptionState()
    }

    fun updateOrder(groupIdList: List<Long>) {
        doAction(feedDatabaseManager.updateGroupsOrder(groupIdList))
    }

    private fun doAction(completable: Completable) {
        if (actionProcessingDisposable == null) {
            actionProcessingDisposable = completable
                .subscribeOn(Schedulers.io())
                .subscribe { mutableEventLiveData.postValue(Event.SuccessEvent) }
        }
    }

    sealed class Event {
        object SuccessEvent : Event()
    }
}
