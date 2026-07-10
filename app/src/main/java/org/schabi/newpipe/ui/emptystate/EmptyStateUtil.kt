@file:JvmName("EmptyStateUtil")

package org.schabi.newpipe.ui.emptystate

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.ui.theme.AppTheme

@JvmOverloads
fun ComposeView.setEmptyStateComposable(
    spec: EmptyStateSpec = EmptyStateSpec.GenericError,
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
) = apply {
    setViewCompositionStrategy(strategy)
    setContent {
        AppTheme {
            EmptyStateComposable(
                spec = spec,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 128.dp)
            )
        }
    }
}
