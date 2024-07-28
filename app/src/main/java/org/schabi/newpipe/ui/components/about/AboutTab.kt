package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
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

@Composable
@NonRestartableComposable
fun AboutTab() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = stringResource(R.string.app_name)
        )
        Text(
            style = MaterialTheme.typography.titleLarge,
            text = stringResource(R.string.app_name)
        )
        Text(text = BuildConfig.VERSION_NAME)
    }

    Text(text = stringResource(R.string.app_description))

    for (item in ABOUT_ITEMS) {
        AboutItem(item)
    }
}

@Composable
@NonRestartableComposable
private fun AboutItem(aboutData: AboutData) {
    Column {
        Text(
            text = stringResource(aboutData.title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = stringResource(aboutData.description))

        val context = LocalContext.current
        TextButton(
            modifier = Modifier.fillMaxWidth()
                .wrapContentWidth(Alignment.End),
            onClick = { ShareUtils.openUrlInApp(context, context.getString(aboutData.url)) }
        ) {
            Text(text = stringResource(aboutData.buttonText))
        }
    }
}
