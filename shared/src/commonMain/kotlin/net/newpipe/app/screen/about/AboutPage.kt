/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.BuildConfig
import net.newpipe.app.Constants
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.ShareHandler
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.logoBackground
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_foreground
import newpipe.shared.generated.resources.yp_about_based_on
import newpipe.shared.generated.resources.yp_about_credits
import newpipe.shared.generated.resources.yp_about_faq
import newpipe.shared.generated.resources.yp_about_licenses
import newpipe.shared.generated.resources.yp_about_privacy
import newpipe.shared.generated.resources.yp_about_section_help
import newpipe.shared.generated.resources.yp_about_section_legal
import newpipe.shared.generated.resources.yp_about_site
import newpipe.shared.generated.resources.yp_about_source
import newpipe.shared.generated.resources.yp_about_summary
import newpipe.shared.generated.resources.yp_about_support
import newpipe.shared.generated.resources.yp_about_version
import newpipe.shared.generated.resources.yp_ic_caret_right
import newpipe.shared.generated.resources.yp_ic_code
import newpipe.shared.generated.resources.yp_ic_external
import newpipe.shared.generated.resources.yp_ic_globe
import newpipe.shared.generated.resources.yp_ic_hand_heart
import newpipe.shared.generated.resources.yp_ic_question
import newpipe.shared.generated.resources.yp_ic_scroll
import newpipe.shared.generated.resources.yp_ic_shield
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val screenMargin = 20.dp

/**
 * YewPlay: the about screen as a single page (proposal, page 16): icon, version and NewPipe
 * base, the license in one sentence, two groups of shortcuts and the credits.
 */
@Composable
fun AboutPage(
    shareHandler: ShareHandler = koinInject(),
    navigator: Navigator = koinInject()
) {
    AboutPageContent(
        onOpenUrl = { url -> shareHandler.openUrlInBrowser(url) },
        onOpenLicenses = { navigator.navigateTo(Destination.Licenses) }
    )
}

@Composable
fun AboutPageContent(
    onOpenUrl: (url: String) -> Unit = {},
    onOpenLicenses: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
        item { AboutHeader() }
        item { LicenseSummary() }

        item { SectionTitle(stringResource(Res.string.yp_about_section_help)) }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_question),
                title = stringResource(Res.string.yp_about_faq),
                external = true,
                onClick = { onOpenUrl(Constants.URL_FAQ) }
            )
        }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_code),
                title = stringResource(Res.string.yp_about_source),
                external = true,
                onClick = { onOpenUrl(Constants.URL_GITHUB) }
            )
        }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_globe),
                title = stringResource(Res.string.yp_about_site),
                external = true,
                onClick = { onOpenUrl(Constants.URL_WEBSITE) }
            )
        }

        item { SectionTitle(stringResource(Res.string.yp_about_section_legal)) }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_shield),
                title = stringResource(Res.string.yp_about_privacy),
                external = false,
                onClick = { onOpenUrl(Constants.URL_PRIVACY) }
            )
        }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_scroll),
                title = stringResource(Res.string.yp_about_licenses),
                external = false,
                onClick = onOpenLicenses
            )
        }
        item {
            AboutRow(
                icon = painterResource(Res.drawable.yp_ic_hand_heart),
                title = stringResource(Res.string.yp_about_support),
                external = false,
                onClick = { onOpenUrl(Constants.URL_DONATION) }
            )
        }

        item {
            Text(
                modifier = Modifier.padding(
                    start = screenMargin,
                    end = screenMargin,
                    top = 20.dp,
                    bottom = 24.dp
                ),
                text = stringResource(Res.string.yp_about_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(percent = 29))
                .background(color = logoBackground),
            painter = painterResource(Res.drawable.ic_foreground),
            contentDescription = BuildConfig.APP_NAME
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = BuildConfig.APP_NAME,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(Res.string.yp_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    Res.string.yp_about_based_on,
                    BuildConfig.BASE_APP_NAME,
                    BuildConfig.BASE_VERSION_NAME
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Libre and ad-free." in bold, followed by the rest of the license summary. */
@Composable
private fun LicenseSummary() {
    val summary = stringResource(Res.string.yp_about_summary)
    val split = summary.indexOf(". ").let { if (it < 0) 0 else it + 1 }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenMargin, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(summary.substring(0, split))
                }
                append(summary.substring(split))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        modifier = Modifier.padding(
            start = screenMargin,
            end = screenMargin,
            top = 20.dp,
            bottom = 4.dp
        ),
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AboutRow(icon: Painter, title: String, external: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .height(56.dp)
                .padding(horizontal = screenMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(
                    if (external) Res.drawable.yp_ic_external else Res.drawable.yp_ic_caret_right
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = screenMargin + 38.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AboutPagePreview() {
    AboutPageContent()
}
