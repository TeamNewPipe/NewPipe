/*
 * SPDX-FileCopyrightText: 2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.launch
import net.newpipe.app.preview.PreviewTemplate
import newpipe.composeapp.generated.resources.Res
import newpipe.composeapp.generated.resources.tab_about
import newpipe.composeapp.generated.resources.tab_licenses
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AboutScreen() {
    ScreenContent()
}

@Composable
private fun ScreenContent(onNavigateUp: () -> Unit = {}) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val pages = listOf(Res.string.tab_about, Res.string.tab_licenses)
            val pagerState = rememberPagerState { pages.size }
            val coroutineScope = rememberCoroutineScope()

            SecondaryTabRow(
                modifier = Modifier.fillMaxWidth(),
                selectedTabIndex = pagerState.currentPage
            ) {
                pages.fastForEachIndexed { index, pageId ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        text = {
                            Text(text = stringResource(pageId))
                        },
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    AboutTab()
                } else {
                    LicenseTab()
                }
            }
        }
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    PreviewTemplate {
        ScreenContent()
    }
}
