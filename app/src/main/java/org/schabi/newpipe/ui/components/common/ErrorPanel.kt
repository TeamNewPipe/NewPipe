package org.schabi.newpipe.ui.components.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraLarge
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingLarge
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingSmall

@Composable
fun ErrorPanel(
    spec: ErrorPanelSpec,
    onRetry: () -> Unit,
    onReport: () -> Unit,
    onOpenInBrowser: () -> Unit,
    modifier: Modifier = Modifier

) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = SpacingLarge, vertical = SpacingMedium)

    ) {

        val message = stringResource(spec.messageRes)

        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        spec.serviceInfoRes?.let { infoRes ->
            Spacer(Modifier.height(SpacingSmall))
            val serviceInfo = stringResource(infoRes)
            Text(
                text = serviceInfo,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        spec.serviceExplanationRes?.let { explanationRes ->
            Spacer(Modifier.height(SpacingSmall))
            val serviceExplanation = stringResource(explanationRes)
            Text(
                text = serviceExplanation,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(SpacingMedium))
        if (spec.showReport) {
            ServiceColoredButton(onClick = onReport) {
                Text(stringResource(R.string.error_snackbar_action).uppercase())
            }
        }
        if (spec.showRetry) {
            ServiceColoredButton(onClick = onRetry) {
                Text(stringResource(R.string.retry).uppercase())
            }
        }
        if (spec.showOpenInBrowser) {
            ServiceColoredButton(onClick = onOpenInBrowser) {
                Text(stringResource(R.string.open_in_browser).uppercase())
            }
        }
        Spacer(Modifier.height(SpacingExtraLarge))
    }
}

data class ErrorPanelSpec(
    @StringRes val messageRes: Int,
    @StringRes val serviceInfoRes: Int? = null,
    val serviceExplanation: String? = null,
    @StringRes val serviceExplanationRes: Int? = null,
    val showRetry: Boolean = false,
    val showReport: Boolean = false,
    val showOpenInBrowser: Boolean = false
)

@Preview(showBackground = true, widthDp = 360, heightDp = 640)

@Composable
fun ErrorPanelPreview() {
    AppTheme {
        ErrorPanel(
            spec = ErrorPanelSpec(
                messageRes = android.R.string.httpErrorBadUrl,
                showRetry = true,
                showReport = false,
                showOpenInBrowser = false
            ),
            onRetry = {},
            onReport = {},
            onOpenInBrowser = {},
            modifier = Modifier
        )
    }
}
