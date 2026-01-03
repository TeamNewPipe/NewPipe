package org.schabi.newpipe.download.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadEntry
import org.schabi.newpipe.download.DownloadStage
import org.schabi.newpipe.fragments.detail.DownloadUiState
import org.schabi.newpipe.fragments.detail.isPending
import org.schabi.newpipe.fragments.detail.isRunning
import us.shandian.giga.util.Utility
import us.shandian.giga.util.Utility.FileType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadStatusHost(
    state: DownloadUiState,
    onChipClick: (DownloadEntry) -> Unit,
    onDismissSheet: () -> Unit,
    onOpenFile: (DownloadEntry) -> Unit,
    onDeleteFile: (DownloadEntry) -> Unit,
    onRemoveLink: (DownloadEntry) -> Unit,
    onShowInFolder: (DownloadEntry) -> Unit
) {
    val selected = state.selected
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.isSheetVisible && selected != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = sheetState
        ) {
            DownloadSheetContent(
                entry = selected,
                onOpenFile = { onOpenFile(selected) },
                onDeleteFile = { onDeleteFile(selected) },
                onRemoveLink = { onRemoveLink(selected) },
                onShowInFolder = { onShowInFolder(selected) }
            )
        }
    }

    if (state.entries.isEmpty()) {
        return
    }

    FlowRow(modifier = Modifier.padding(horizontal = 12.dp)) {
        state.entries.forEach { entry ->
            DownloadChip(entry = entry, onClick = { onChipClick(entry) })
        }
    }
}

@Composable
private fun DownloadChip(entry: DownloadEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val type = Utility.getFileType(entry.handle.kind, entry.displayName ?: "")
    val backgroundColor = Utility.getBackgroundForFileType(context, type)
    val stripeColor = Utility.getForegroundForFileType(context, type)

    val typeLabelRes = when (type) {
        FileType.MUSIC -> R.string.download_type_audio
        FileType.VIDEO -> R.string.download_type_video
        FileType.SUBTITLE -> R.string.download_type_captions
        FileType.UNKNOWN -> R.string.download_type_media
    }
    val typeLabel = stringResource(typeLabelRes)

    val stageText = when (entry.stage) {
        DownloadStage.Finished -> stringResource(R.string.download_status_downloaded_type, typeLabel)
        DownloadStage.Running -> stringResource(R.string.download_status_downloading_type, typeLabel)
        DownloadStage.Pending -> stringResource(R.string.download_status_pending_type, typeLabel)
    }

    val chipText = entry.qualityLabel?.takeIf { it.isNotBlank() }?.let { "$stageText • $it" } ?: stageText

    val chipShape = MaterialTheme.shapes.small

    val baseModifier = Modifier
        .padding(end = 8.dp, bottom = 8.dp)
        .clip(chipShape)
        .drawWithContent {
            if (entry.stage == DownloadStage.Finished) {
                drawRect(Color(backgroundColor))
                drawContent()
            } else if (entry.isPending) {
                drawRect(Color(backgroundColor))
                val stripePaint = Color(stripeColor).copy(alpha = 0.35f)
                val stripeWidth = 12.dp.toPx()
                var offset = -size.height
                val diagonal = size.height
                while (offset < size.width + size.height) {
                    drawLine(
                        color = stripePaint,
                        start = Offset(offset, 0f),
                        end = Offset(offset + diagonal, diagonal),
                        strokeWidth = stripeWidth
                    )
                    offset += stripeWidth * 2f
                }
                drawContent()
            } else {
                drawContent()
            }
        }

    val labelColor = MaterialTheme.colorScheme.onSurface

    val chipColors = AssistChipDefaults.assistChipColors(
        containerColor = Color.Transparent,
        labelColor = labelColor
    )

    AssistChip(
        onClick = onClick,
        label = { Text(text = chipText) },
        colors = chipColors,
        modifier = baseModifier,
        border = null
    )
}

@Composable
private fun DownloadSheetContent(
    entry: DownloadEntry,
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
        val title = entry.displayName ?: stringResource(id = R.string.download)
        Text(text = title, style = MaterialTheme.typography.titleLarge)

        val subtitleParts = buildList {
            entry.qualityLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
            when (entry.stage) {
                DownloadStage.Finished -> if (!entry.fileAvailable) {
                    add(stringResource(id = R.string.download_status_missing))
                }
                DownloadStage.Pending, DownloadStage.Running -> {
                    if (entry.isRunning) {
                        add(stringResource(R.string.download_status_downloading))
                    } else {
                        add(stringResource(R.string.missions_header_pending))
                    }
                }
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

        val showFileActions = entry.fileAvailable && entry.fileUri != null
        if (showFileActions) {
            TextButton(onClick = onOpenFile) {
                Text(text = stringResource(id = R.string.open_with))
            }
            TextButton(onClick = onShowInFolder, enabled = entry.parentUri != null) {
                Text(text = stringResource(id = R.string.download_action_show_in_folder))
            }
            TextButton(onClick = onDeleteFile) {
                Text(text = stringResource(id = R.string.delete_file))
            }
        }

        TextButton(onClick = onRemoveLink, enabled = entry.stage == DownloadStage.Finished) {
            Text(
                text = stringResource(id = R.string.delete_entry),
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
