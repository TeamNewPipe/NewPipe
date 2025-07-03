package org.schabi.newpipe.ui.UiModel

import androidx.compose.runtime.Immutable
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.ErrorPanelSpec

/**
 * Each concrete case from this hierarchy represents a different failure state that the UI can render with ErrorPanel
 */
@Immutable
sealed interface ErrorUiModel {
    val spec: ErrorPanelSpec
    val rawError: Throwable? get() = null
}

/**
 * Concrete cases - Comments unable to load, Comments disabled, No connectivity, DNS failure, timeout etc
 */
@Immutable
data class UnableToLoadCommentsUiModel(override val rawError: Throwable?) : ErrorUiModel {
    override val spec: ErrorPanelSpec =
        ErrorPanelSpec(
            messageRes = R.string.error_unable_to_load_comments,
            showRetry = true,
            showReport = true,
            showOpenInBrowser = false
        )
}

/**
 * A generic ErrorUiModel for unhandled cases
 */
@Immutable
data class GenericErrorUiModel(override val rawError: Throwable?) : ErrorUiModel {
    override val spec: ErrorPanelSpec =
        ErrorPanelSpec(
            messageRes = R.string.general_error,
            showRetry = true,
            showReport = true,
        )
}
