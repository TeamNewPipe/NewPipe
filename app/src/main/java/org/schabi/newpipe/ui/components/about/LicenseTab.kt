package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import my.nanihadesuka.compose.LazyColumnScrollbar
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.NewPipeScrollbarSettings

@Composable
@NonRestartableComposable
fun LicenseTab() {
    val lazyListState = rememberLazyListState()

    LazyColumnScrollbar(state = lazyListState, settings = NewPipeScrollbarSettings) {
        LibrariesContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            lazyListState = lazyListState,
            header = {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.app_license_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.app_license),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.title_licenses),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        )
    }
}
