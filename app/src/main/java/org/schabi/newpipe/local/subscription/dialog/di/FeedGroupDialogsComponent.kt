package org.schabi.newpipe.local.subscription.dialog.di

import dagger.Subcomponent
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog

@Subcomponent(modules = [FeedGroupDialogsModule::class])
interface FeedGroupDialogsComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(): FeedGroupDialogsComponent
    }

    fun inject(feedGroupDialog: FeedGroupDialog)
    fun inject(feedGroupReorderDialog: FeedGroupReorderDialog)
}
