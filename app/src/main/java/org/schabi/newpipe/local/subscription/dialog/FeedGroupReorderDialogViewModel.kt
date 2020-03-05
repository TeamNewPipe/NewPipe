package org.schabi.newpipe.local.subscription.dialog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager

class FeedGroupReorderDialogViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)

    val groupsLiveData = MutableLiveData<List<FeedGroupEntity>>()
    val dialogEventLiveData = MutableLiveData<DialogEvent>()

    private var actionProcessingDisposable: Disposable? = null

    private var groupsDisposable = feedDatabaseManager.groups()
            .limit(1)
            .subscribeOn(Schedulers.io())
            .subscribe(groupsLiveData::postValue)

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
            dialogEventLiveData.value = DialogEvent.ProcessingEvent

            actionProcessingDisposable = completable
                    .subscribeOn(Schedulers.io())
                    .subscribe { dialogEventLiveData.postValue(DialogEvent.SuccessEvent) }
        }
    }

    sealed class DialogEvent {
        object ProcessingEvent : DialogEvent()
        object SuccessEvent : DialogEvent()
    }
}