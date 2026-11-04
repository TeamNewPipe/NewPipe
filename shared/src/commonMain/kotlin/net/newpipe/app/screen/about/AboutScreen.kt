/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.yp_about_licenses
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * YewPlay: "About YewPlay" is a single screen; the licenses open in their own screen.
 */
@Composable
fun AboutScreen(navigator: Navigator = koinInject()) {
    AboutScreenContent(
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun AboutScreenContent(
    onNavigateUp: () -> Unit = {},
    content: @Composable () -> Unit = { AboutPage() }
) {
    Scaffold(
        topBar = { TopAppBar(onNavigateUp = onNavigateUp) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            content()
        }
    }
}

/**
 * YewPlay: list of the YewPlay license and the third-party licenses.
 */
@Composable
fun LicensesScreen(navigator: Navigator = koinInject()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.yp_about_licenses),
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LicensePage()
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    AboutScreenContent(content = { AboutPageContent() })
}
