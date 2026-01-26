package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.LoadingIndicator

@Composable
@NonRestartableComposable
fun LicenseTab(viewModel: LicenseTabViewModel = viewModel()) {
    val lazyListState = rememberLazyListState()
    val stateFlow = viewModel.state.collectAsState()
    val state = stateFlow.value

    if (state.licenseDialogHtml != null) {
        LicenseDialog(
            licenseHtml = state.licenseDialogHtml,
            onDismissRequest = { viewModel.closeLicenseDialog() }
        )
    }

    LazyColumnThemedScrollbar(state = lazyListState) {
        LazyColumn(
            state = lazyListState
        ) {
            item {
                Text(
                    text = stringResource(R.string.app_license_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    )
                )
            }
            item {
                Text(
                    text = stringResource(R.string.app_license),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    )
                )
            }
            if (state.firstPartyLibraries == null) {
                item {
                    LoadingIndicator(modifier = Modifier.padding(32.dp))
                }
            } else {
                for (library in state.firstPartyLibraries) {
                    item {
                        Library(
                            library = library,
                            showLicenseDialog = viewModel::showLicenseDialog,
                            descriptionMaxLines = Int.MAX_VALUE
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.title_licenses),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    )
                )
            }
            if (state.thirdPartyLibraries == null) {
                item {
                    LoadingIndicator(modifier = Modifier.padding(32.dp))
                }
            } else {
                for (library in state.thirdPartyLibraries) {
                    item {
                        Library(
                            library = library,
                            showLicenseDialog = viewModel::showLicenseDialog,
                            descriptionMaxLines = 2
                        )
                    }
                }
            }
        }
    }
}
