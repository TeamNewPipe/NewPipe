@file:JvmName("EmptyStateUtil")

package org.schabi.newpipe.ui.emptystate

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.schabi.newpipe.ui.theme.AppTheme

@JvmOverloads
fun ComposeView.setEmptyStateComposable(
    spec: EmptyStateSpec = EmptyStateSpec.GenericError,
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
) = apply {
    setViewCompositionStrategy(strategy)
    setContent {
        AppTheme {
            EmptyStateComposable(
                spec = spec
            )
        }
    }
}
