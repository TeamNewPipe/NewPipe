@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.LoadingIndicator

@Composable
fun LicenseDialog(licenseHtml: AnnotatedString, onDismissRequest: () -> Unit) {
    val lazyListState = rememberLazyListState()

    ModalBottomSheet(onDismissRequest) {
        CompositionLocalProvider(
            // contentColorFor(MaterialTheme.colorScheme.containerColor), i.e. ModalBottomSheet's
            // default background color, does not resolve correctly, so need to manually set the
            // content color for MaterialTheme.colorScheme.background instead
            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background)
        ) {
            LazyColumnThemedScrollbar(state = lazyListState) {
                LazyColumn(
                    state = lazyListState
                ) {
                    item {
                        if (licenseHtml.isEmpty()) {
                            LoadingIndicator(modifier = Modifier.padding(32.dp))
                        } else {
                            Text(
                                text = licenseHtml,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
