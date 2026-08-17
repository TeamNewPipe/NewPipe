package org.schabi.newpipe.local.subscription.dialog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager

class FeedGroupReorderDialogViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)

    private val mutableGroupsLiveData = MutableLiveData<List<FeedGroupEntity>>()
    private val mutableDialogEventLiveData = MutableLiveData<DialogEvent>()
    val groupsLiveData: LiveData<List<FeedGroupEntity>> = mutableGroupsLiveData
    val dialogEventLiveData: LiveData<DialogEvent> = mutableDialogEventLiveData

    private var isActionProcessing = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = feedDatabaseManager.groups().first()
            mutableGroupsLiveData.postValue(groups)
        }
    }

    fun updateOrder(groupIdList: List<Long>) {
        doAction {
            feedDatabaseManager.updateGroupsOrder(groupIdList)
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

    sealed class DialogEvent {
        data object ProcessingEvent : DialogEvent()
        data object SuccessEvent : DialogEvent()
    }
}
