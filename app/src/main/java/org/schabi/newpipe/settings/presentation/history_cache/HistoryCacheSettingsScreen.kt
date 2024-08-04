package org.schabi.newpipe.settings.presentation.history_cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.schabi.newpipe.settings.presentation.history_cache.components.CachePreferencesComponent
import org.schabi.newpipe.settings.presentation.history_cache.components.HistoryPreferencesComponent
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun HistoryCacheScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryCacheSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    HistoryCacheComponent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier
    )
}

@Composable
fun HistoryCacheComponent(
    state: HistoryCacheUiState,
    onEvent: (HistoryCacheEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackBarHostState = remember { SnackbarHostState() }
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
                state = state.switchPreferencesUiState,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth()
            )
            val coroutineScope = rememberCoroutineScope()
            CachePreferencesComponent(
                onEvent = onEvent,
                onShowSnackbar = {
                    coroutineScope.launch {
                        snackBarHostState.showSnackbar(it)
                    }
                },
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
            HistoryCacheUiState()
        )
    }
    AppTheme(
        useDarkTheme = false
    ) {
        Surface {
            HistoryCacheComponent(
                state = state,
                onEvent = {
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
