/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.BuildConfig
import net.newpipe.app.Constants
import net.newpipe.app.composable.about.LinkListItem
import net.newpipe.app.model.Link
import net.newpipe.app.platform.ShareHandler
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.iconTVDPI
import net.newpipe.app.theme.logoBackground
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceXSmall
import net.newpipe.app.theme.spaceXXSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.app_description
import newpipe.shared.generated.resources.contribution_encouragement
import newpipe.shared.generated.resources.contribution_title
import newpipe.shared.generated.resources.donation_encouragement
import newpipe.shared.generated.resources.donation_title
import newpipe.shared.generated.resources.faq
import newpipe.shared.generated.resources.faq_description
import newpipe.shared.generated.resources.faq_title
import newpipe.shared.generated.resources.give_back
import newpipe.shared.generated.resources.ic_foreground
import newpipe.shared.generated.resources.open_in_browser
import newpipe.shared.generated.resources.privacy_policy_encouragement
import newpipe.shared.generated.resources.privacy_policy_title
import newpipe.shared.generated.resources.read_privacy_policy
import newpipe.shared.generated.resources.view_on_github
import newpipe.shared.generated.resources.website_encouragement
import newpipe.shared.generated.resources.website_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AboutPage(shareHandler: ShareHandler = koinInject()) {
    AboutPageContent(
        onOpenUrl = { url -> shareHandler.openUrlInBrowser(url) }
    )
}

@Composable
fun AboutPageContent(
    links: List<Link> = defaultLinks(),
    onOpenUrl: (url: String) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(spaceXXSmall)
    ) {
        // Page Header
        item {
            Column(
                modifier = Modifier
                    .padding(spaceLarge)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier
                        .requiredSize(iconTVDPI)
                        .clip(CircleShape)
                        .background(color = logoBackground),
                    painter = painterResource(Res.drawable.ic_foreground),
                    contentDescription = BuildConfig.APP_NAME
                )
                Spacer(modifier = Modifier.height(spaceXSmall))
                Text(
                    text = BuildConfig.APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Res.string.app_description),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Links about NewPipe
        items(items = links, key = { link -> link.url }) { link ->
            LinkListItem(
                link = link,
                onAction = { onOpenUrl(link.url) }
            )
        }
    }
}

@Composable
private fun defaultLinks() = listOf(
    Link(
        title = stringResource(Res.string.faq_title),
        description = stringResource(Res.string.faq_description),
        action = stringResource(Res.string.faq),
        url = Constants.URL_FAQ
    ),
    Link(
        title = stringResource(Res.string.contribution_title),
        description = stringResource(Res.string.contribution_encouragement),
        action = stringResource(Res.string.view_on_github),
        url = Constants.URL_GITHUB
    ),
    Link(
        title = stringResource(Res.string.donation_title),
        description = stringResource(Res.string.donation_encouragement),
        action = stringResource(Res.string.give_back),
        url = Constants.URL_DONATION
    ),
    Link(
        title = stringResource(Res.string.website_title),
        description = stringResource(Res.string.website_encouragement),
        action = stringResource(Res.string.open_in_browser),
        url = Constants.URL_WEBSITE
    ),
    Link(
        title = stringResource(Res.string.privacy_policy_title),
        description = stringResource(Res.string.privacy_policy_encouragement),
        action = stringResource(Res.string.read_privacy_policy),
        url = Constants.URL_PRIVACY
    )
)

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AboutPagePreview() {
    AboutPageContent()
}
