package org.schabi.newpipe.download.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.download.CompletedDownload
import org.schabi.newpipe.fragments.detail.DownloadChipState
import org.schabi.newpipe.fragments.detail.DownloadUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStatusHost(
    state: DownloadUiState,
    onChipClick: () -> Unit,
    onDismissSheet: () -> Unit,
    onOpenFile: (CompletedDownload) -> Unit,
    onDeleteFile: (CompletedDownload) -> Unit,
    onRemoveLink: (CompletedDownload) -> Unit,
    onShowInFolder: (CompletedDownload) -> Unit
) {
    val chipState = state.chipState
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.isSheetVisible && chipState is DownloadChipState.Downloaded) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = sheetState
        ) {
            DownloadSheetContent(
                info = chipState.info,
                onOpenFile = { onOpenFile(chipState.info) },
                onDeleteFile = { onDeleteFile(chipState.info) },
                onRemoveLink = { onRemoveLink(chipState.info) },
                onShowInFolder = { onShowInFolder(chipState.info) }
            )
        }
    }

    when (chipState) {
        DownloadChipState.Hidden -> Unit
        DownloadChipState.Downloading -> AssistChip(
            onClick = onChipClick,
            label = { Text(text = stringResource(id = R.string.download_status_downloading)) }
        )
        is DownloadChipState.Downloaded -> {
            val label = chipState.info.qualityLabel
            val text = if (!label.isNullOrBlank()) {
                stringResource(R.string.download_status_downloaded, label)
            } else {
                stringResource(R.string.download_status_downloaded_simple)
            }
            AssistChip(
                onClick = onChipClick,
                label = { Text(text = text) }
            )
        }
    }
}

@Composable
private fun DownloadSheetContent(
    info: CompletedDownload,
    onOpenFile: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemoveLink: () -> Unit,
    onShowInFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        val title = info.displayName ?: stringResource(id = R.string.download)
        Text(text = title, style = MaterialTheme.typography.titleLarge)

        val subtitleParts = buildList {
            info.qualityLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (!info.fileAvailable) {
                add(stringResource(id = R.string.download_status_missing))
            }
        }
        if (subtitleParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitleParts.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val showFileActions = info.fileAvailable && info.fileUri != null
        if (showFileActions) {
            TextButton(onClick = onOpenFile) {
                Text(text = stringResource(id = R.string.download_action_open))
            }
            TextButton(onClick = onShowInFolder, enabled = info.parentUri != null) {
                Text(text = stringResource(id = R.string.download_action_show_in_folder))
            }
            TextButton(onClick = onDeleteFile) {
                Text(text = stringResource(id = R.string.download_action_delete))
            }
        }

        TextButton(onClick = onRemoveLink) {
            Text(text = stringResource(id = R.string.download_action_remove_link), color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
