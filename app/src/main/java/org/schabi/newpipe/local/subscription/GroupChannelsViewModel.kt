package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.profiles.ProfileManager

class GroupChannelsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NewPipeDatabase.getInstance(application)
    private val subscriptionManager = SubscriptionManager(application)
    private val profileId = ProfileManager.getActiveProfileId(application)
    data class State(
        val channels: List<SubscriptionEntity> = emptyList(),
        val loading: Boolean = false,
        val error: Throwable? = null
    )

    private val mutableState = MutableLiveData(State(loading = true))
    val state: LiveData<State> = mutableState
    private var disposable: Disposable? = null

    fun load(groupId: Long) {
        disposable?.dispose()
        mutableState.value = State(loading = true)
        val source = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> subscriptionManager.subscriptions()

            FeedGroupEntity.GROUP_UNGROUPED_ID -> database.subscriptionDAO().getUngroupedSubscriptionsForProfile(profileId)

            else -> database.feedGroupDAO()
                .getSubscriptionIdsForProfile(profileId, groupId)
                .switchMap { ids ->
                    subscriptionManager.subscriptions().map { subscriptions ->
                        subscriptions.filter { it.uid in ids }
                    }
                }
        }
        disposable = source
            .subscribeOn(Schedulers.io())
            .distinctUntilChanged()
            .subscribe(
                { mutableState.postValue(State(channels = it)) },
                { mutableState.postValue(State(error = it)) }
            )
    }

    override fun onCleared() {
        disposable?.dispose()
    }
}
