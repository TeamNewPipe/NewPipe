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
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem

class FeedGroupDialogViewModel(
    applicationContext: Context,
    private val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    initialQuery: String = "",
    initialShowOnlyUngrouped: Boolean = false
) : ViewModel() {

    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(applicationContext)
    private var subscriptionManager = SubscriptionManager(applicationContext)

    private var filterSubscriptions = BehaviorProcessor.create<String>()
    private var toggleShowOnlyUngrouped = BehaviorProcessor.create<Boolean>()

    private var subscriptionsFlowable = Flowable
        .combineLatest(
                filterSubscriptions.startWith(initialQuery),
                toggleShowOnlyUngrouped.startWith(initialShowOnlyUngrouped),
                BiFunction { t1: String, t2: Boolean -> Filter(t1, t2) }
        )
        .distinctUntilChanged()
        .switchMap { filter ->
            subscriptionManager.getSubscriptions(groupId, filter.query, filter.showOnlyUngrouped)
        }.map { list -> list.map { PickerSubscriptionItem(it) } }

    private val mutableGroupLiveData = MutableLiveData<FeedGroupEntity>()
    private val mutableSubscriptionsLiveData = MutableLiveData<Pair<List<PickerSubscriptionItem>, Set<Long>>>()
    private val mutableDialogEventLiveData = MutableLiveData<DialogEvent>()
    val groupLiveData: LiveData<FeedGroupEntity> = mutableGroupLiveData
    val subscriptionsLiveData: LiveData<Pair<List<PickerSubscriptionItem>, Set<Long>>> = mutableSubscriptionsLiveData
    val dialogEventLiveData: LiveData<DialogEvent> = mutableDialogEventLiveData

    private var actionProcessingDisposable: Disposable? = null

    private var feedGroupDisposable = feedDatabaseManager.getGroup(groupId)
        .subscribeOn(Schedulers.io())
        .subscribe(mutableGroupLiveData::postValue)

    private var subscriptionsDisposable = Flowable
        .combineLatest(subscriptionsFlowable, feedDatabaseManager.subscriptionIdsForGroup(groupId),
            BiFunction { t1: List<PickerSubscriptionItem>, t2: List<Long> -> t1 to t2.toSet() })
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

    fun filterSubscriptionsBy(query: String) {
        filterSubscriptions.onNext(query)
    }

    fun clearSubscriptionsFilter() {
        filterSubscriptions.onNext("")
    }

    fun toggleShowOnlyUngrouped(showOnlyUngrouped: Boolean) {
        toggleShowOnlyUngrouped.onNext(showOnlyUngrouped)
    }

    sealed class DialogEvent {
        object ProcessingEvent : DialogEvent()
        object SuccessEvent : DialogEvent()
    }

    data class Filter(val query: String, val showOnlyUngrouped: Boolean)

    class Factory(
        private val context: Context,
        private val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        private val initialQuery: String = "",
        private val initialShowOnlyUngrouped: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return FeedGroupDialogViewModel(context.applicationContext,
                groupId, initialQuery, initialShowOnlyUngrouped) as T
        }
    }
}
