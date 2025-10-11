package org.schabi.newpipe.settings.presentation.history_cache.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.components.irreversible_preference.IrreversiblePreferenceComponent
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium

@Composable
fun CachePreferencesComponent(
    recaptchaCookiesEnabled: Boolean,
    onEvent: (HistoryCacheEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogTitle by remember { mutableStateOf("") }
    var dialogOnClick by remember { mutableStateOf({}) }
    var isDialogVisible by remember { mutableStateOf(false) }

    val deleteViewHistory = stringResource(id = R.string.delete_view_history_alert)
    val deletePlayBacks = stringResource(id = R.string.delete_playback_states_alert)
    val deleteSearchHistory = stringResource(id = R.string.delete_search_history_alert)

    val onOpenDialog: (String, HistoryCacheEvent) -> Unit = { title, eventType ->
        dialogTitle = title
        isDialogVisible = true
        dialogOnClick = {
            onEvent(eventType)
            isDialogVisible = false
        }
    }

    Column(
        modifier = modifier,
    ) {
        Text(
            stringResource(id = R.string.settings_category_clear_data_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(SpacingMedium)
        )
        IrreversiblePreferenceComponent(
            title = stringResource(id = R.string.metadata_cache_wipe_title),
            summary = stringResource(id = R.string.metadata_cache_wipe_summary),
            onClick = {
                onEvent(HistoryCacheEvent.OnClickWipeCachedMetadata(R.string.metadata_cache_wipe_key))
            },
            modifier = Modifier.fillMaxWidth()
        )
        IrreversiblePreferenceComponent(
            title = stringResource(id = R.string.clear_views_history_title),
            summary = stringResource(id = R.string.clear_views_history_summary),
            onClick = {
                onOpenDialog(
                    deleteViewHistory,
                    HistoryCacheEvent.OnClickClearWatchHistory(R.string.clear_views_history_key)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        IrreversiblePreferenceComponent(
            title = stringResource(id = R.string.clear_playback_states_title),
            summary = stringResource(id = R.string.clear_playback_states_summary),
            onClick = {
                onOpenDialog(
                    deletePlayBacks,
                    HistoryCacheEvent.OnClickDeletePlaybackPositions(R.string.clear_playback_states_key)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        IrreversiblePreferenceComponent(
            title = stringResource(id = R.string.clear_search_history_title),
            summary = stringResource(id = R.string.clear_search_history_summary),
            onClick = {
                onOpenDialog(
                    deleteSearchHistory,
                    HistoryCacheEvent.OnClickClearSearchHistory(R.string.clear_search_history_key)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        IrreversiblePreferenceComponent(
            title = stringResource(id = R.string.clear_cookie_title),
            summary = stringResource(id = R.string.clear_cookie_summary),
            onClick = {
                onEvent(HistoryCacheEvent.OnClickReCaptchaCookies(R.string.recaptcha_cookies_key))
            },
            enabled = recaptchaCookiesEnabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (isDialogVisible) {
            CacheAlertDialog(
                dialogTitle = dialogTitle,
                onClickCancel = { isDialogVisible = false },
                onClick = dialogOnClick
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CachePreferencesComponentPreview() {
    AppTheme {
        Scaffold { padding ->
            CachePreferencesComponent(
                recaptchaCookiesEnabled = false,
                onEvent = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun CacheAlertDialog(
    dialogTitle: String,
    onClickCancel: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onClickCancel,
        confirmButton = {
            TextButton(onClick = onClick) {
                Text(text = "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onClickCancel) {
                Text(text = "Cancel")
            }
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = "This is an irreversible action")
        },
        modifier = modifier
    )
}

@Preview(backgroundColor = 0xFFFFFFFF)
@Composable
private fun CacheAlertDialogPreview() {
    AppTheme {
        Scaffold { padding ->
            CacheAlertDialog(
                dialogTitle = "Delete view history",
                onClickCancel = {},
                onClick = {},
                modifier = Modifier.padding(padding)
            )
        }
    }
}
