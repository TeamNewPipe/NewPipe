@file:JvmName("EmptyStateUtil")

package org.schabi.newpipe.ui.emptystate

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.ui.theme.AppTheme

@JvmOverloads
fun ComposeView.setEmptyStateComposable(
    spec: EmptyStateSpec = EmptyStateSpec.GenericError,
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
) = apply {
    setViewCompositionStrategy(strategy)
    setContent {
        AppTheme {
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background)
            ) {
                EmptyStateComposable(
                    spec = spec,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)

                )
            }
        }
    }
}
