@file:JvmName("EmptyStateUtil")

package org.schabi.newpipe.ui.emptystate

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.errorHint
import androidx.compose.runtime.mutableStateOf as composeRuntimeMutableStateOf

@JvmOverloads
fun ComposeView.setEmptyStateText(
    @StringRes stringRes: Int,
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
) = apply {
    setViewCompositionStrategy(strategy)
    setContent {
        AppTheme {
            Text(
                text = stringResource(id = stringRes),
                color = MaterialTheme.colorScheme.errorHint,
            )
        }
    }
}

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
class EmptyStateSpecBuilder(var spec: EmptyStateSpec) {

    fun descriptionText(@StringRes stringRes: Int) = apply {
        spec = spec.copy(
            descriptionText = { stringResource(id = stringRes) }
        )
    }

    fun descriptionVisibility(descriptionTextVisibility: Boolean) = apply {
        spec = spec.copy(descriptionVisibility = { descriptionTextVisibility })
    }

    fun build() = spec
}
