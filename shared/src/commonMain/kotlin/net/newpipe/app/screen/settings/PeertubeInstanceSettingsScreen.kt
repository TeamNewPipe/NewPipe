/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ReorderableItem
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.composable.rememberReorderableListState
import net.newpipe.app.model.PeertubeInstance
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.PeertubePreferences.DEFAULT_INSTANCE
import net.newpipe.app.preferences.PeertubePreferences.INSTANCE_LIST_URL
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.model.PeertubeInstanceMessage
import net.newpipe.app.viewmodel.settings.PeertubeInstanceSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.ic_add
import newpipe.shared.generated.resources.ic_drag_handle
import newpipe.shared.generated.resources.ic_placeholder_peertube
import newpipe.shared.generated.resources.ic_settings_backup_restore
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.peertube_instance_add_exists
import newpipe.shared.generated.resources.peertube_instance_add_fail
import newpipe.shared.generated.resources.peertube_instance_add_help
import newpipe.shared.generated.resources.peertube_instance_add_https_only
import newpipe.shared.generated.resources.peertube_instance_add_title
import newpipe.shared.generated.resources.peertube_instance_url_help
import newpipe.shared.generated.resources.peertube_instance_url_title
import newpipe.shared.generated.resources.restore_defaults
import newpipe.shared.generated.resources.restore_defaults_confirmation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val HELP_KEY = "help"
private const val INSTANCE_CONTENT_TYPE = "instance"

@Composable
fun PeertubeInstanceSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: PeertubeInstanceSettingsViewModel = koinViewModel()
) {
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedInstance by viewModel.selectedInstance.collectAsStateWithLifecycle()
    val isAdding by viewModel.isAdding.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    PeertubeInstanceSettingsScreenContent(
        instances = instances,
        selectedUrl = selectedInstance.url,
        isAdding = isAdding,
        message = message,
        onSelect = viewModel::select,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
        onRestoreDefaults = viewModel::restoreDefaults,
        onMessageShown = viewModel::onMessageShown,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun PeertubeInstanceSettingsScreenContent(
    instances: List<PeertubeInstance> = listOf(DEFAULT_INSTANCE),
    selectedUrl: String = DEFAULT_INSTANCE.url,
    isAdding: Boolean = false,
    message: PeertubeInstanceMessage? = null,
    onSelect: (PeertubeInstance) -> Unit = {},
    onAdd: (String) -> Unit = {},
    onRemove: (PeertubeInstance) -> Unit = {},
    onMove: (fromUrl: String, toUrl: String) -> Unit = { _, _ -> },
    onRestoreDefaults: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    // Instance rows are keyed by URL, so the keys handed back are always strings
    val reorderState = rememberReorderableListState(
        onMove = { fromKey, toKey -> onMove(fromKey as String, toKey as String) }
    )

    val messageText = message?.let { pending ->
        when (pending) {
            PeertubeInstanceMessage.HTTPS_ONLY -> {
                stringResource(Res.string.peertube_instance_add_https_only)
            }

            PeertubeInstanceMessage.ALREADY_EXISTS -> {
                stringResource(Res.string.peertube_instance_add_exists)
            }

            PeertubeInstanceMessage.ADD_FAILED -> {
                stringResource(Res.string.peertube_instance_add_fail)
            }
        }
    }

    LaunchedEffect(message) {
        if (messageText == null) {
            return@LaunchedEffect
        }
        // Showing the snackbar from the surrounding scope instead of this effect, so that marking
        // the message as seen below cannot cancel it by restarting the effect
        scope.launch { snackbarHostState.showSnackbar(messageText) }
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.peertube_instance_url_title),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_settings_backup_restore),
                            contentDescription = stringResource(Res.string.restore_defaults)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.peertube_instance_add_title)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = reorderState.lazyListState,
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                item(key = HELP_KEY) {
                    PeertubeInstanceHelp()
                }

                items(
                    items = instances,
                    key = { instance -> instance.url },
                    contentType = { INSTANCE_CONTENT_TYPE }
                ) { instance ->
                    ReorderableItem(state = reorderState, key = instance.url) { handleModifier ->
                        PeertubeInstanceRow(
                            instance = instance,
                            selected = instance.url == selectedUrl,
                            onSelect = { onSelect(instance) },
                            onRemove = { onRemove(instance) },
                            handleModifier = handleModifier
                        )
                    }
                }
            }

            if (isAdding) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showAddDialog) {
        AddInstanceDialog(
            onAdd = { url ->
                showAddDialog = false
                onAdd(url)
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(text = stringResource(Res.string.restore_defaults)) },
            text = { Text(text = stringResource(Res.string.restore_defaults_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        onRestoreDefaults()
                    }
                ) {
                    Text(text = stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PeertubeInstanceHelp() {
    val text = stringResource(Res.string.peertube_instance_url_help, INSTANCE_LIST_URL)
    val linkColor = MaterialTheme.colorScheme.primary
    // Only the URL is a link, like the legacy autoLink text
    val linkedText = remember(text, linkColor) {
        buildAnnotatedString {
            append(text)
            val start = text.indexOf(INSTANCE_LIST_URL)
            if (start >= 0) {
                addLink(
                    LinkAnnotation.Url(
                        url = INSTANCE_LIST_URL,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    ),
                    start,
                    start + INSTANCE_LIST_URL.length
                )
            }
        }
    }

    Text(
        text = linkedText,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PeertubeInstanceRow(
    instance: PeertubeInstance,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    handleModifier: Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        // The selected instance can't be removed, so it can't be swiped away either
        gesturesEnabled = !selected
    ) {
        ListItem(
            modifier = Modifier.selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
            leadingContent = {
                Image(
                    painter = painterResource(Res.drawable.ic_placeholder_peertube),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            headlineContent = {
                Text(text = instance.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(text = instance.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Icon(
                        painter = painterResource(Res.drawable.ic_drag_handle),
                        contentDescription = null,
                        modifier = handleModifier.padding(start = 16.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun AddInstanceDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textFieldState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = painterResource(Res.drawable.ic_placeholder_peertube),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = { Text(text = stringResource(Res.string.peertube_instance_add_title)) },
        text = {
            TextField(
                state = textFieldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(Res.string.peertube_instance_add_help)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(textFieldState.text.toString()) }) {
                Text(text = stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PeertubeInstanceSettingsScreenPreview() {
    PeertubeInstanceSettingsScreenContent(
        instances = listOf(
            DEFAULT_INSTANCE,
            PeertubeInstance(name = "Tilvids", url = "https://tilvids.com")
        )
    )
}
