package org.schabi.newpipe.settings.presentation.history_cache.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.components.switch_preference.SwitchPreferenceComponent
import org.schabi.newpipe.settings.presentation.history_cache.HistoryCacheEvent
import org.schabi.newpipe.settings.presentation.history_cache.SwitchPreferencesUiState
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun HistoryPreferencesComponent(
    state: SwitchPreferencesUiState,
    onEvent: (HistoryCacheEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        val preferences = rememberSwitchPreferencesUiState(state)
        preferences.forEach { preference ->
            val key = stringResource(preference.keyId)
            SwitchPreferenceComponent(
                title = stringResource(id = preference.titleId),
                summary = stringResource(id = preference.summaryId),
                isChecked = preference.isEnabled,
                onCheckedChange = {
                    onEvent(HistoryCacheEvent.OnUpdateBooleanPreference(key, it))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun rememberSwitchPreferencesUiState(
    state: SwitchPreferencesUiState,
): List<SwitchPreferencesIdState> {
    val preferences by remember {
        mutableStateOf(
            listOf(
                SwitchPreferencesIdState(
                    titleId = R.string.enable_watch_history_title,
                    summaryId = R.string.enable_watch_history_summary,
                    isEnabled = state.watchHistoryEnabled,
                    keyId = R.string.enable_watch_history_key
                ),
                SwitchPreferencesIdState(
                    titleId = R.string.enable_playback_resume_title,
                    summaryId = R.string.enable_playback_resume_summary,
                    isEnabled = state.resumePlaybackEnabled,
                    keyId = R.string.enable_playback_resume_key
                ),
                SwitchPreferencesIdState(
                    titleId = R.string.enable_playback_state_lists_title,
                    summaryId = R.string.enable_playback_state_lists_summary,
                    isEnabled = state.positionsInListsEnabled,
                    keyId = R.string.enable_playback_state_lists_key
                ),
                SwitchPreferencesIdState(
                    titleId = R.string.enable_search_history_title,
                    summaryId = R.string.enable_search_history_summary,
                    isEnabled = state.searchHistoryEnabled,
                    keyId = R.string.enable_search_history_key
                )
            )
        )
    }
    return preferences
}

private data class SwitchPreferencesIdState(
    val titleId: Int,
    val summaryId: Int,
    val isEnabled: Boolean,
    val keyId: Int,
)

@Preview(showBackground = true)
@Composable
private fun SwitchPreferencesComponentPreview() {
    var state by remember {
        mutableStateOf(
            SwitchPreferencesUiState()
        )
    }
    AppTheme(
        useDarkTheme = false
    ) {
        Scaffold { padding ->
            HistoryPreferencesComponent(
                state = state,
                onEvent = {
                    // Mock behaviour to preview
                    state = state.copy(
                        watchHistoryEnabled = !state.watchHistoryEnabled
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            )
        }
    }
}
