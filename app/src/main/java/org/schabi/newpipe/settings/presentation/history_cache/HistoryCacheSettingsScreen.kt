package org.schabi.newpipe.settings.presentation.history_cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.presentation.history_cache.components.CachePreferencesComponent
import org.schabi.newpipe.settings.presentation.history_cache.components.HistoryPreferencesComponent
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowClearWatchHistorySnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowDeletePlaybackSnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowDeleteSearchHistorySnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowReCaptchaCookiesSnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowWipeCachedMetadataSnackbar
import org.schabi.newpipe.settings.presentation.history_cache.state.SwitchPreferencesUiState
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun HistoryCacheSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryCacheSettingsViewModel = hiltViewModel(),
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val playBackPositionsDeleted = stringResource(R.string.watch_history_states_deleted)
    val watchHistoryDeleted = stringResource(R.string.watch_history_deleted)
    val wipeCachedMetadataSnackbar = stringResource(R.string.metadata_cache_wipe_complete_notice)
    val deleteSearchHistory = stringResource(R.string.search_history_deleted)
    val clearReCaptchaCookiesSnackbar = stringResource(R.string.recaptcha_cookies_cleared)

    LaunchedEffect(key1 = true) {
        viewModel.onInit()
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is ShowDeletePlaybackSnackbar -> playBackPositionsDeleted
                is ShowClearWatchHistorySnackbar -> watchHistoryDeleted
                is ShowWipeCachedMetadataSnackbar -> wipeCachedMetadataSnackbar
                is ShowDeleteSearchHistorySnackbar -> deleteSearchHistory
                is ShowReCaptchaCookiesSnackbar -> clearReCaptchaCookiesSnackbar
            }

            snackBarHostState.showSnackbar(message)
        }
    }

    val switchPreferencesUiState by viewModel.switchState.collectAsState()
    val recaptchaCookiesEnabled by viewModel.captchaCookies.collectAsState()
    HistoryCacheComponent(
        switchPreferences = switchPreferencesUiState,
        recaptchaCookiesEnabled = recaptchaCookiesEnabled,
        onEvent = { viewModel.onEvent(it) },
        snackBarHostState = snackBarHostState,
        modifier = modifier
    )
}

@Composable
fun HistoryCacheComponent(
    switchPreferences: SwitchPreferencesUiState,
    recaptchaCookiesEnabled: Boolean,
    onEvent: (HistoryCacheEvent) -> Unit,
    snackBarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HistoryPreferencesComponent(
                state = switchPreferences,
                onEvent = { key, value ->
                    onEvent(HistoryCacheEvent.OnUpdateBooleanPreference(key, value))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.fillMaxWidth())
            CachePreferencesComponent(
                recaptchaCookiesEnabled = recaptchaCookiesEnabled,
                onEvent = { onEvent(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryCacheComponentPreview() {
    val state by remember {
        mutableStateOf(
            SwitchPreferencesUiState()
        )
    }
    AppTheme(
        useDarkTheme = false
    ) {
        Surface {
            HistoryCacheComponent(
                switchPreferences = state,
                recaptchaCookiesEnabled = false,
                onEvent = {
                },
                snackBarHostState = SnackbarHostState(),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
