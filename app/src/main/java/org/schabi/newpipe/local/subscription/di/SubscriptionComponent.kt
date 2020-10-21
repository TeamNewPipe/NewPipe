package org.schabi.newpipe.local.subscription.di

import dagger.Subcomponent
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.local.subscription.services.BaseImportExportService

@Subcomponent(modules = [SubscriptionModule::class])
interface SubscriptionComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(): SubscriptionComponent
    }

    fun inject(baseImportExportService: BaseImportExportService)
    fun inject(subscriptionFragment: SubscriptionFragment)
}
