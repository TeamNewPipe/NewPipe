package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.SubscriptionManager

class SubscriptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val subscriptionManager = SubscriptionManager(application)

    val subscriptions: StateFlow<List<SubscriptionEntity>> = subscriptionManager.subscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
