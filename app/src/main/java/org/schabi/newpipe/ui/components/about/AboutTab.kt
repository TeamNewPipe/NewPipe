package org.schabi.newpipe.ui.components.about

import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import my.nanihadesuka.compose.ColumnScrollbar
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.defaultThemedScrollbarSettings
import org.schabi.newpipe.util.external_communication.ShareUtils

private val ABOUT_ITEMS = listOf(
    AboutData(R.string.faq_title, R.string.faq_description, R.string.faq, R.string.faq_url),
    AboutData(
        R.string.contribution_title, R.string.contribution_encouragement,
        R.string.view_on_github, R.string.github_url
    ),
    AboutData(
        R.string.donation_title, R.string.donation_encouragement, R.string.give_back,
        R.string.donation_url
    ),
    AboutData(
        R.string.website_title, R.string.website_encouragement, R.string.open_in_browser,
        R.string.website_url
    ),
    AboutData(
        R.string.privacy_policy_title, R.string.privacy_policy_encouragement,
        R.string.read_privacy_policy, R.string.privacy_policy_url
    )
)

private class AboutData(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val buttonText: Int,
    @StringRes val url: Int
)

private class AboutDataProvider : CollectionPreviewParameterProvider<AboutData>(ABOUT_ITEMS)

@Preview(backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
@NonRestartableComposable
fun AboutTab() {
    val scrollState = rememberScrollState()

    ColumnScrollbar(state = scrollState, settings = defaultThemedScrollbarSettings()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // note: the preview
                val icon = AppCompatResources.getDrawable(LocalContext.current, R.mipmap.ic_launcher)
                Image(
                    painter = rememberDrawablePainter(icon),
                    contentDescription = stringResource(R.string.app_name),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.app_description),
                    textAlign = TextAlign.Center,
                )
            }

            for (item in ABOUT_ITEMS) {
                AboutItem(item, Modifier.padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Preview(backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
@NonRestartableComposable
private fun AboutItem(
    @PreviewParameter(AboutDataProvider::class) aboutData: AboutData,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(aboutData.title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(aboutData.description),
            style = MaterialTheme.typography.bodyMedium
        )

        val context = LocalContext.current
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.End),
            onClick = { ShareUtils.openUrlInApp(context, context.getString(aboutData.url)) }
        ) {
            Text(text = stringResource(aboutData.buttonText))
        }
    }
}
