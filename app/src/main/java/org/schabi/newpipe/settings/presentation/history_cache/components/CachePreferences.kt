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
import org.schabi.newpipe.settings.presentation.history_cache.HistoryCacheEvent
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium

@Composable
fun CachePreferencesComponent(
    onEvent: (HistoryCacheEvent) -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = rememberPreferences()
    var dialogTitle by remember { mutableStateOf("") }
    var dialogOnClick by remember { mutableStateOf({}) }
    var isDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
    ) {
        Text(
            stringResource(id = R.string.settings_category_clear_data_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(SpacingMedium)
        )
        preferences.forEach {
            val elementDialogTitle = it.dialogTitle?.let { dialogTitle ->
                stringResource(dialogTitle)
            }
            val elementSnackBarText = stringResource(it.snackBarText)
            val key = stringResource(id = it.keyId)
            val onClick = {
                if (elementDialogTitle != null) {
                    dialogTitle = elementDialogTitle
                    isDialogVisible = true
                    dialogOnClick = {
                        onEvent(it.event(key))
                        isDialogVisible = false
                        onShowSnackbar(elementSnackBarText)
                    }
                } else {
                    onEvent(it.event(key))
                    onShowSnackbar(elementSnackBarText)
                }
            }
            IrreversiblePreferenceComponent(
                title = stringResource(id = it.title),
                summary = stringResource(it.summary),
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        CacheAlertDialog(
            isDialogVisible = isDialogVisible,
            dialogTitle = dialogTitle,
            onClickCancel = { isDialogVisible = false },
            onClick = dialogOnClick
        )
    }
}

@Composable
private fun rememberPreferences(): List<IrreversiblePreferenceUiState> {
    val preferences by remember {
        mutableStateOf(
            listOf(
                IrreversiblePreferenceUiState(
                    title = R.string.metadata_cache_wipe_title,
                    summary = R.string.metadata_cache_wipe_summary,
                    dialogTitle = null,
                    snackBarText = R.string.metadata_cache_wipe_complete_notice,
                    event = { HistoryCacheEvent.OnClickWipeCachedMetadata(it) },
                    keyId = R.string.metadata_cache_wipe_key,
                ),
                IrreversiblePreferenceUiState(
                    title = R.string.clear_views_history_title,
                    summary = R.string.clear_views_history_summary,
                    dialogTitle = R.string.delete_view_history_alert,
                    snackBarText = R.string.watch_history_deleted,
                    event = { HistoryCacheEvent.OnClickClearWatchHistory(it) },
                    keyId = R.string.clear_views_history_key,
                ),
                IrreversiblePreferenceUiState(
                    title = R.string.clear_playback_states_title,
                    summary = R.string.clear_playback_states_summary,
                    dialogTitle = R.string.delete_playback_states_alert,
                    snackBarText = R.string.watch_history_states_deleted,
                    event = { HistoryCacheEvent.OnClickDeletePlaybackPositions(it) },
                    keyId = R.string.clear_playback_states_key,
                ),
                IrreversiblePreferenceUiState(
                    title = R.string.clear_search_history_title,
                    summary = R.string.clear_search_history_summary,
                    dialogTitle = R.string.delete_search_history_alert,
                    snackBarText = R.string.search_history_deleted,
                    event = { HistoryCacheEvent.OnClickClearSearchHistory(it) },
                    keyId = R.string.clear_search_history_key,
                ),
                IrreversiblePreferenceUiState(
                    title = R.string.clear_cookie_title,
                    summary = R.string.clear_cookie_summary,
                    dialogTitle = null,
                    snackBarText = R.string.recaptcha_cookies_cleared,
                    event = { HistoryCacheEvent.OnClickReCaptchaCookies(it) },
                    keyId = R.string.recaptcha_cookies_key,
                )
            )
        )
    }
    return preferences
}

private data class IrreversiblePreferenceUiState(
    val title: Int,
    val summary: Int,
    val dialogTitle: Int?,
    val snackBarText: Int,
    val enabled: Boolean = true,
    val event: (String) -> HistoryCacheEvent,
    val keyId: Int,
)

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CachePreferencesComponentPreview() {
    AppTheme {
        Scaffold { padding ->
            CachePreferencesComponent(
                onEvent = {},
                onShowSnackbar = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun CacheAlertDialog(
    isDialogVisible: Boolean,
    dialogTitle: String,
    onClickCancel: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isDialogVisible) {
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
}

@Preview(backgroundColor = 0xFFFFFFFF)
@Composable
private fun CacheAlertDialogPreview() {
    AppTheme {
        Scaffold { padding ->
            CacheAlertDialog(
                isDialogVisible = true,
                dialogTitle = "Delete view history",
                onClickCancel = {},
                onClick = {},
                modifier = Modifier.padding(padding)
            )
        }
    }
}
