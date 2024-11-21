@file:JvmName("EmptyStateUtil")

package org.schabi.newpipe.ui.emptystate

import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.ui.theme.AppTheme
import androidx.compose.runtime.mutableStateOf as composeRuntimeMutableStateOf

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

@JvmOverloads
fun ComposeView.setEmptyStateComposable(
    spec: State<EmptyStateSpec>,
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
) = apply {
    setViewCompositionStrategy(strategy)
    setContent {
        AppTheme {
            EmptyStateComposable(
                spec = spec.value,
            )
        }
    }
}

/**
 * Used in Java land to bridge the [MutableState] API.
 */
fun <T> mutableStateOf(param: T): MutableState<T> {
    return composeRuntimeMutableStateOf(param)
}

/**
 * Used in Java land to modify [EmptyStateSpec] properties.
 * TODO: remove after Kotlin migration
 */
class EmptyStateSpecBuilder(private var spec: EmptyStateSpec) {

    fun descriptionText(@StringRes stringRes: Int) = apply {
        spec = spec.copy(
            descriptionText = { stringResource(id = stringRes) }
        )
    }

    fun build() = spec
}
