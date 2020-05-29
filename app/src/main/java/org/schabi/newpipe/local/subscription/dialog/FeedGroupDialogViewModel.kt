package org.schabi.newpipe.local.subscription.dialog

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.SubscriptionManager

class FeedGroupDialogViewModel(applicationContext: Context, val groupId: Long = FeedGroupEntity.GROUP_ALL_ID) : ViewModel() {
    class Factory(val context: Context, val groupId: Long = FeedGroupEntity.GROUP_ALL_ID) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return FeedGroupDialogViewModel(context.applicationContext, groupId) as T
        }
    }

    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(applicationContext)
    private var subscriptionManager = SubscriptionManager(applicationContext)

    private val mutableGroupLiveData = MutableLiveData<FeedGroupEntity>()
    private val mutableSubscriptionsLiveData = MutableLiveData<Pair<List<SubscriptionEntity>, Set<Long>>>()
    private val mutableDialogEventLiveData = MutableLiveData<DialogEvent>()
    val groupLiveData: LiveData<FeedGroupEntity> = mutableGroupLiveData
    val subscriptionsLiveData: LiveData<Pair<List<SubscriptionEntity>, Set<Long>>> = mutableSubscriptionsLiveData
    val dialogEventLiveData: LiveData<DialogEvent> = mutableDialogEventLiveData

    private var actionProcessingDisposable: Disposable? = null

    private var feedGroupDisposable = feedDatabaseManager.getGroup(groupId)
            .subscribeOn(Schedulers.io())
            .subscribe(mutableGroupLiveData::postValue)

    private var subscriptionsDisposable = Flowable
            .combineLatest(subscriptionManager.subscriptions(), feedDatabaseManager.subscriptionIdsForGroup(groupId),
                    BiFunction { t1: List<SubscriptionEntity>, t2: List<Long> -> t1 to t2.toSet() })
            .subscribeOn(Schedulers.io())
            .subscribe(mutableSubscriptionsLiveData::postValue)

    override fun onCleared() {
        super.onCleared()
        actionProcessingDisposable?.dispose()
        subscriptionsDisposable.dispose()
        feedGroupDisposable.dispose()
    }

    fun createGroup(name: String, selectedIcon: FeedGroupIcon, selectedSubscriptions: Set<Long>) {
        doAction(feedDatabaseManager.createGroup(name, selectedIcon)
                .flatMapCompletable {
                    feedDatabaseManager.updateSubscriptionsForGroup(it, selectedSubscriptions.toList())
                })
    }

    fun updateGroup(name: String, selectedIcon: FeedGroupIcon, selectedSubscriptions: Set<Long>, sortOrder: Long) {
        doAction(feedDatabaseManager.updateSubscriptionsForGroup(groupId, selectedSubscriptions.toList())
                .andThen(feedDatabaseManager.updateGroup(FeedGroupEntity(groupId, name, selectedIcon, sortOrder))))
    }

    fun deleteGroup() {
        doAction(feedDatabaseManager.deleteGroup(groupId))
    }

    private fun doAction(completable: Completable) {
        if (actionProcessingDisposable == null) {
            mutableDialogEventLiveData.value = DialogEvent.ProcessingEvent

            actionProcessingDisposable = completable
                    .subscribeOn(Schedulers.io())
                    .subscribe { mutableDialogEventLiveData.postValue(DialogEvent.SuccessEvent) }
        }
    }

    sealed class DialogEvent {
        object ProcessingEvent : DialogEvent()
        object SuccessEvent : DialogEvent()
    }
}
