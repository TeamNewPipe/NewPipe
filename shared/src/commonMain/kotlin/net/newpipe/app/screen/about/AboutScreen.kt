/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.launch
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.about.navigation.Page
import net.newpipe.app.theme.currentServiceScheme
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.title_activity_about
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AboutScreen(navigator: Navigator = koinInject()) {
    AboutScreenContent(
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun AboutScreenContent(
    pages: List<Page> = listOf(Page.ABOUT, Page.LICENSE),
    onNavigateUp: () -> Unit = {},
    serviceScheme: ColorScheme = currentServiceScheme(),
    onPageContent: @Composable (page: Page) -> Unit = { page ->
        when (page) {
            Page.ABOUT -> AboutPage()
            Page.LICENSE -> LicensePage()
        }
    }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.title_activity_about),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            val pagerState = rememberPagerState { pages.size }
            val coroutineScope = rememberCoroutineScope()

            SecondaryTabRow(
                modifier = Modifier.fillMaxWidth(),
                containerColor = serviceScheme.primaryContainer,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(
                            selectedTabIndex = pagerState.currentPage,
                            matchContentSize = false
                        ),
                        color = serviceScheme.onPrimaryContainer
                    )
                },
                selectedTabIndex = pagerState.currentPage
            ) {
                pages.fastForEachIndexed { index, page ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        text = {
                            Text(
                                text = stringResource(page.localizedTitle),
                                color = serviceScheme.onPrimaryContainer
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }

            HorizontalPager(modifier = Modifier.fillMaxSize(), state = pagerState) { page ->
                onPageContent(pages[page])
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    AboutScreenContent(
        onPageContent = { page ->
            when (page) {
                Page.ABOUT -> AboutPageContent()
                Page.LICENSE -> LicensePageContent()
            }
        }
    )
}
