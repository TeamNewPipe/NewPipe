package org.schabi.newpipe.ui.components.common

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.timeago.patterns.it
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraLarge
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingSmall
import org.schabi.newpipe.util.external_communication.ShareUtils

enum class ErrorAction(@StringRes val actionStringId: Int) {
    REPORT(R.string.error_snackbar_action),
    SOLVE_CAPTCHA(R.string.recaptcha_solve)
}

/**
 * Determines the error action type based on the throwable in ErrorInfo
 *
 */
fun determineErrorAction(errorInfo: ErrorInfo): ErrorAction {
    return when (errorInfo.throwable) {
        is ReCaptchaException -> ErrorAction.SOLVE_CAPTCHA
        is AccountTerminatedException -> ErrorAction.REPORT
        else -> ErrorAction.REPORT
    }
}

@Composable
fun ErrorPanel(
    errorInfo: ErrorInfo,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier

) {
    val explanation = errorInfo.getExplanation()
    val canOpenInBrowser = errorInfo.openInBrowserUrl != null
    val errorActionType = determineErrorAction(errorInfo)

    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,

    ) {

        Text(
            text = stringResource(errorInfo.messageStringId),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        if (explanation.isNotBlank()) {
            Spacer(Modifier.height(SpacingSmall))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(SpacingMedium))
        when (errorActionType) {
            ErrorAction.REPORT -> {
                ServiceColoredButton(onClick = {
                    ErrorUtil.openActivity(context, errorInfo)
                }) {
                    Text(stringResource(errorActionType.actionStringId).uppercase())
                }
            }
            ErrorAction.SOLVE_CAPTCHA -> {
                ServiceColoredButton(onClick = {
                    // Starting ReCaptcha Challenge Activity
                    val intent = Intent(context, ReCaptchaActivity::class.java)
                        .putExtra(
                            ReCaptchaActivity.RECAPTCHA_URL_EXTRA,
                            (errorInfo.throwable as ReCaptchaException).url
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(errorActionType.actionStringId).uppercase())
                }
            }
        }

        onRetry?.let {
            ServiceColoredButton(onClick = it) {
                Text(stringResource(R.string.retry).uppercase())
            }
        }
        if (canOpenInBrowser) {
            ServiceColoredButton(onClick = {
                errorInfo.openInBrowserUrl?.let { url ->
                    ShareUtils.openUrlInBrowser(context, url)
                }
            }) {
                Text(stringResource(R.string.open_in_browser).uppercase())
            }
        }

        Spacer(Modifier.height(SpacingExtraLarge))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)

@Composable
fun ErrorPanelPreview() {
    AppTheme {
    }
}
