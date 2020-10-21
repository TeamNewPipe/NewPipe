package org.schabi.newpipe.local.subscription.dialog.di

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import org.schabi.newpipe.di.ViewModelKey
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialogViewModel

@Module
abstract class FeedGroupDialogsModule {
    @Binds
    @IntoMap
    @ViewModelKey(FeedGroupReorderDialogViewModel::class)
    abstract fun bindViewModel(viewModel: FeedGroupReorderDialogViewModel): ViewModel
}
