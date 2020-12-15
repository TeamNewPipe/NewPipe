package org.schabi.newpipe.local.subscription.dialog

import android.app.Application
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager

class FeedGroupReorderDialogViewModel @ViewModelInject constructor(
    application: Application,
    private var feedDatabaseManager: FeedDatabaseManager
) : AndroidViewModel(application) {
    private val mutableGroupsLiveData = MutableLiveData<List<FeedGroupEntity>>()
    private val mutableDialogEventLiveData = MutableLiveData<DialogEvent>()
    val groupsLiveData: LiveData<List<FeedGroupEntity>> = mutableGroupsLiveData
    val dialogEventLiveData: LiveData<DialogEvent> = mutableDialogEventLiveData

    private var actionProcessingDisposable: Disposable? = null

    private var groupsDisposable = feedDatabaseManager.groups()
        .take(1)
        .subscribeOn(Schedulers.io())
        .subscribe(mutableGroupsLiveData::postValue)

    override fun onCleared() {
        super.onCleared()
        actionProcessingDisposable?.dispose()
        groupsDisposable.dispose()
    }

    fun updateOrder(groupIdList: List<Long>) {
        doAction(feedDatabaseManager.updateGroupsOrder(groupIdList))
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
