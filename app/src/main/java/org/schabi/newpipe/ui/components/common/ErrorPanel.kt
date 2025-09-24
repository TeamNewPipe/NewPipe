package org.schabi.newpipe.ui.components.common

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens
import org.schabi.newpipe.util.external_communication.ShareUtils

@Composable
fun ErrorPanel(
    errorInfo: ErrorInfo,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,

) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val messageText = if (isPreview) {
        stringResource(R.string.error_snackbar_message)
    } else {
        errorInfo.getMessage(context)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {

        Text(
            text = messageText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(SizeTokens.SpacingMedium))
        if (errorInfo.isReportable) {
            ServiceColoredButton(onClick = {
                ErrorUtil.openActivity(context, errorInfo)
            }) {
                Text(stringResource(R.string.error_snackbar_action).uppercase())
            }
        }

        errorInfo.recaptchaUrl?.let { recaptchaUrl ->
            ServiceColoredButton(onClick = {
                val intent = Intent(context, ReCaptchaActivity::class.java)
                    .putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, recaptchaUrl)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.recaptcha_solve).uppercase())
            }
        }

        if (errorInfo.isRetryable) {
            onRetry?.let {
                ServiceColoredButton(onClick = it) {
                    Text(stringResource(R.string.retry).uppercase())
                }
            }
        }

        errorInfo.openInBrowserUrl?.let { url ->
            ServiceColoredButton(onClick = {
                ShareUtils.openUrlInBrowser(context, url)
            }) {
                Text(stringResource(R.string.open_in_browser).uppercase())
            }
        }

        Spacer(Modifier.height(SizeTokens.SpacingExtraLarge))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)

@Composable
fun ErrorPanelPreview() {
    AppTheme {
        ErrorPanel(
            errorInfo = ErrorInfo(
                throwable = Exception("Network error"),
                userAction = org.schabi.newpipe.error.UserAction.UI_ERROR,
                request = "Preview request"
            )
        )
    }
}
