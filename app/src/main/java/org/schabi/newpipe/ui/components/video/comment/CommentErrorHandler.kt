package org.schabi.newpipe.ui.components.video.comment

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.SideEffect
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.error.mapThrowableToErrorUiModel
import org.schabi.newpipe.ui.UiModel.ErrorUiModel
import org.schabi.newpipe.ui.components.common.ErrorPanel
import java.io.IOException

@Composable
fun CommentErrorHandler(
    throwable: Throwable,
    userAction: UserAction,
    onRetry: () -> Unit,
    onReport: (ErrorInfo) -> Unit
) {
    SideEffect {
        Log.d("CommentErrorHandler", "⛔️ rendered for error: ${throwable.message}")
    }

    val uiModel: ErrorUiModel = mapThrowableToErrorUiModel(throwable, userAction)
    val errorInfo = ErrorInfo(
        throwable = throwable,
        userAction = userAction,
        request = ""
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ErrorPanel(
            spec = uiModel.spec,
            onRetry = onRetry,
            onReport = { onReport(errorInfo) },
            onOpenInBrowser = {},
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewCommentErrorHandler() {
    CommentErrorHandler(
        throwable = IOException("No network"),
        userAction = UserAction.REQUESTED_COMMENTS,
        onRetry = {},
        onReport = {}
    )
}
