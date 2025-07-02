package org.schabi.newpipe.error

import org.schabi.newpipe.ui.UiModel.ErrorUiModel
import org.schabi.newpipe.ui.UiModel.GenericErrorUiModel
import org.schabi.newpipe.ui.UiModel.UnableToLoadCommentsUiModel

fun mapThrowableToErrorUiModel(throwable: Throwable, userAction: UserAction? = null): ErrorUiModel {
    if (userAction == UserAction.REQUESTED_COMMENTS) {

        return UnableToLoadCommentsUiModel(rawError = throwable)
    }
    // Other ErrorInfo logic and throwable +  user actions
    return GenericErrorUiModel(rawError = throwable)
}
