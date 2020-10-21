package org.schabi.newpipe.local.subscription.di

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import org.schabi.newpipe.di.ViewModelKey
import org.schabi.newpipe.local.subscription.SubscriptionViewModel

@Module
abstract class SubscriptionModule {
    @Binds
    @IntoMap
    @ViewModelKey(SubscriptionViewModel::class)
    abstract fun bindViewModel(subscriptionViewModel: SubscriptionViewModel): ViewModel
}
