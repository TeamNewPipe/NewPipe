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
import org.schabi.newpipe.settings.presentation.history_cache.state.SwitchPreferencesUiState
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun HistoryPreferencesComponent(
    state: SwitchPreferencesUiState,
    onEvent: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        SwitchPreferenceComponent(
            title = stringResource(id = R.string.enable_watch_history_title),
            summary = stringResource(id = R.string.enable_watch_history_summary),
            isChecked = state.watchHistoryEnabled,
            onCheckedChange = {
                onEvent(R.string.enable_watch_history_key, it)
            },
            modifier = Modifier.fillMaxWidth()
        )
        SwitchPreferenceComponent(
            title = stringResource(id = R.string.enable_playback_resume_title),
            summary = stringResource(id = R.string.enable_playback_resume_summary),
            isChecked = state.resumePlaybackEnabled,
            onCheckedChange = {
                onEvent(R.string.enable_playback_resume_key, it)
            },
            modifier = Modifier.fillMaxWidth()
        )
        SwitchPreferenceComponent(
            title = stringResource(id = R.string.enable_playback_state_lists_title),
            summary = stringResource(id = R.string.enable_playback_state_lists_summary),
            isChecked = state.positionsInListsEnabled,
            onCheckedChange = {
                onEvent(R.string.enable_playback_state_lists_key, it)
            },
            modifier = Modifier.fillMaxWidth()
        )
        SwitchPreferenceComponent(
            title = stringResource(id = R.string.enable_search_history_title),
            summary = stringResource(id = R.string.enable_search_history_summary),
            isChecked = state.searchHistoryEnabled,
            onCheckedChange = {
                onEvent(R.string.enable_search_history_key, it)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

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
                onEvent = { _, _ ->
                    // Mock behaviour to preview
                    state = state.copy(
                        watchHistoryEnabled = !state.watchHistoryEnabled
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
            )
        }
    }
}
