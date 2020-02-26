package org.schabi.newpipe.local.subscription.dialog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager

class FeedGroupReorderDialogViewModel(application: Application) : AndroidViewModel(application) {
    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(application)

    val groupsLiveData = MutableLiveData<List<FeedGroupEntity>>()
    val dialogEventLiveData = MutableLiveData<DialogEvent>()

    private val disposables = CompositeDisposable()

    private var groupsDisposable = feedDatabaseManager.groups()
            .limit(1)
            .subscribeOn(Schedulers.io())
            .subscribe(groupsLiveData::postValue)

    override fun onCleared() {
        super.onCleared()
        groupsDisposable.dispose()
        disposables.dispose()
    }

    fun updateOrder(groupIdList: List<Long>) {
        disposables.add(feedDatabaseManager.updateGroupsOrder(groupIdList)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { dialogEventLiveData.postValue(DialogEvent.SuccessEvent) })
    }

    sealed class DialogEvent {
        object SuccessEvent : DialogEvent()
    }
}