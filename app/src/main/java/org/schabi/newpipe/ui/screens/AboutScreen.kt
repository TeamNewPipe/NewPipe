package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.about.AboutTab
import org.schabi.newpipe.ui.components.about.LicenseTab
import org.schabi.newpipe.ui.theme.AppTheme

private val TITLES = intArrayOf(R.string.tab_about, R.string.tab_licenses)

@Composable
@NonRestartableComposable
fun AboutScreen(padding: PaddingValues) {
    Column(modifier = Modifier.padding(padding)) {
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val pagerState = rememberPagerState { TITLES.size }

        LaunchedEffect(tabIndex) {
            pagerState.animateScrollToPage(tabIndex)
        }
        LaunchedEffect(pagerState.currentPage) {
            tabIndex = pagerState.currentPage
        }

        TabRow(selectedTabIndex = tabIndex) {
            TITLES.forEachIndexed { index, titleId ->
                Tab(
                    text = { Text(text = stringResource(titleId)) },
                    selected = tabIndex == index,
                    onClick = { tabIndex = index }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 0) {
                AboutTab()
            } else {
                LicenseTab()
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutScreenPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AboutScreen(PaddingValues(8.dp))
        }
    }
}
