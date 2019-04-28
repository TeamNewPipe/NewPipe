package org.schabi.newpipe.local.feed

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.DEFAULT_THROTTLE_TIMEOUT
import java.util.concurrent.TimeUnit

class FeedViewModel(applicationContext: Context, val groupId: Long = -1) : ViewModel() {
    class Factory(val context: Context, val groupId: Long = -1) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return FeedViewModel(context.applicationContext, groupId) as T
        }
    }

    private var feedDatabaseManager: FeedDatabaseManager = FeedDatabaseManager(applicationContext)
    private var subscriptionManager: SubscriptionManager = SubscriptionManager(applicationContext)

    val stateLiveData = MutableLiveData<FeedState>()

    private var combineDisposable = Flowable
            .combineLatest(
                    FeedEventManager.events(),
                    feedDatabaseManager.asStreamItems(groupId),
                    subscriptionManager.subscriptionTable().rowCount(),

                    Function3 { t1: FeedEventManager.Event, t2: List<StreamInfoItem>, t3: Long -> return@Function3 Triple(first = t1, second = t2, third = t3) }
            )
            .throttleLatest(DEFAULT_THROTTLE_TIMEOUT, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                val (event, listFromDB, subsCount) = it

                var lastUpdated = feedDatabaseManager.getLastUpdated(applicationContext)
                if (subsCount == 0L && lastUpdated != null) {
                    feedDatabaseManager.setLastUpdated(applicationContext, null)
                    lastUpdated = null
                }

                stateLiveData.postValue(when (event) {
                    is FeedEventManager.Event.IdleEvent -> FeedState.LoadedState(lastUpdated, listFromDB)
                    is FeedEventManager.Event.ProgressEvent -> FeedState.ProgressState(event.currentProgress, event.maxProgress, event.progressMessage)
                    is FeedEventManager.Event.SuccessResultEvent -> FeedState.LoadedState(lastUpdated, listFromDB, event.itemsErrors)
                    is FeedEventManager.Event.ErrorResultEvent -> throw event.error
                })

                if (event is FeedEventManager.Event.ErrorResultEvent || event is FeedEventManager.Event.SuccessResultEvent) {
                    FeedEventManager.reset()
                }
            }

    override fun onCleared() {
        super.onCleared()
        combineDisposable.dispose()
    }
}