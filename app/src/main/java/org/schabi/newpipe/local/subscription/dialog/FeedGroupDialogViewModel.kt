package org.schabi.newpipe.local.subscription.dialog

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.SubscriptionManager

class FeedGroupDialogViewModel(
    applicationContext: Context,
    private val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    initialQuery: String = "",
    initialShowOnlyUngrouped: Boolean = false
) : ViewModel() {

    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(applicationContext)
    private var subscriptionManager = SubscriptionManager(applicationContext)

    private val filterSubscriptions = MutableStateFlow(initialQuery)
    private val toggleShowOnlyUngrouped = MutableStateFlow(initialShowOnlyUngrouped)

    private val subscriptionsFlow = combine(
        filterSubscriptions,
        toggleShowOnlyUngrouped
    ) { query, showOnlyUngrouped -> Filter(query, showOnlyUngrouped) }
        .distinctUntilChanged()
        .flatMapLatest { (query, showOnlyUngrouped) ->
            subscriptionManager.getSubscriptions(groupId, query, showOnlyUngrouped)
        }

    private val mutableGroupLiveData = MutableLiveData<FeedGroupEntity>()
    private val mutableSubscriptionsLiveData = MutableLiveData<Pair<List<SubscriptionEntity>, Set<Long>>>()
    private val mutableDialogEventLiveData = MutableLiveData<DialogEvent>()
    val groupLiveData: LiveData<FeedGroupEntity> = mutableGroupLiveData
    val subscriptionsLiveData: LiveData<Pair<List<SubscriptionEntity>, Set<Long>>> = mutableSubscriptionsLiveData
    val dialogEventLiveData: LiveData<DialogEvent> = mutableDialogEventLiveData

    private var isActionProcessing = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            feedDatabaseManager.getGroup(groupId)?.let {
                mutableGroupLiveData.postValue(it)
            }
        }

        viewModelScope.launch {
            combine(
                subscriptionsFlow,
                feedDatabaseManager.subscriptionIdsForGroup(groupId)
            ) { items, ids -> items to ids.toSet() }
                .collect { mutableSubscriptionsLiveData.postValue(it) }
        }
    }

    fun createGroup(name: String, selectedIcon: FeedGroupIcon, selectedSubscriptions: Set<Long>) {
        doAction {
            val newGroupId = feedDatabaseManager.createGroup(name, selectedIcon)
            feedDatabaseManager.updateSubscriptionsForGroup(newGroupId, selectedSubscriptions.toList())
        }
    }

    fun updateGroup(name: String, selectedIcon: FeedGroupIcon, selectedSubscriptions: Set<Long>, sortOrder: Long) {
        doAction {
            feedDatabaseManager.updateSubscriptionsForGroup(groupId, selectedSubscriptions.toList())
            feedDatabaseManager.updateGroup(FeedGroupEntity(groupId, name, selectedIcon, sortOrder))
        }
    }

    fun deleteGroup() {
        doAction {
            feedDatabaseManager.deleteGroup(groupId)
        }
    }

    private fun doAction(action: suspend () -> Unit) {
        if (!isActionProcessing) {
            isActionProcessing = true
            mutableDialogEventLiveData.value = DialogEvent.ProcessingEvent

            viewModelScope.launch(Dispatchers.IO) {
                action()
                mutableDialogEventLiveData.postValue(DialogEvent.SuccessEvent)
            }
        }
    }

    fun filterSubscriptionsBy(query: String) {
        filterSubscriptions.value = query
    }

    fun clearSubscriptionsFilter() {
        filterSubscriptions.value = ""
    }

    fun toggleShowOnlyUngrouped(showOnlyUngrouped: Boolean) {
        this.toggleShowOnlyUngrouped.value = showOnlyUngrouped
    }

    sealed class DialogEvent {
        data object ProcessingEvent : DialogEvent()
        data object SuccessEvent : DialogEvent()
    }

    data class Filter(val query: String, val showOnlyUngrouped: Boolean)

    companion object {
        fun getFactory(
            context: Context,
            groupId: Long,
            initialQuery: String,
            initialShowOnlyUngrouped: Boolean
        ) = viewModelFactory {
            initializer {
                FeedGroupDialogViewModel(
                    context.applicationContext,
                    groupId,
                    initialQuery,
                    initialShowOnlyUngrouped
                )
            }
        }
    }
}
