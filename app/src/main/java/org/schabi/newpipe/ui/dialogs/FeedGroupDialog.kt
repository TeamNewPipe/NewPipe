package org.schabi.newpipe.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel

@Composable
fun FeedGroupDialog(
    groupId: Long = -1L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FeedGroupDialogViewModel = viewModel(
        factory = FeedGroupDialogViewModel.getFactory(context, groupId, "", false)
    )
    
    val group by viewModel.groupLiveData.observeAsState()
    val subscriptionsData by viewModel.subscriptionsLiveData.observeAsState()
    val dialogEvent by viewModel.dialogEventLiveData.observeAsState()
    
    var currentScreen by remember { mutableStateOf(FeedGroupScreen.INITIAL) }
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(FeedGroupIcon.ALL) }
    val selectedSubscriptions = remember { mutableStateListOf<Long>() }
    
    LaunchedEffect(group) {
        group?.let {
            name = it.name
            selectedIcon = it.icon
        }
    }
    
    LaunchedEffect(subscriptionsData) {
        subscriptionsData?.second?.let { ids ->
            if (selectedSubscriptions.isEmpty()) {
                selectedSubscriptions.addAll(ids)
            }
        }
    }
    
    LaunchedEffect(dialogEvent) {
        if (dialogEvent is FeedGroupDialogViewModel.DialogEvent.SuccessEvent) {
            onDismiss()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (currentScreen) {
                    FeedGroupScreen.INITIAL -> if (groupId == -1L) stringResource(R.string.feed_groups_header_title) else "Edit Group"
                    FeedGroupScreen.ICON_PICKER -> "Select Icon"
                    FeedGroupScreen.SUBSCRIPTIONS_PICKER -> stringResource(R.string.feed_group_dialog_select_subscriptions)
                    FeedGroupScreen.DELETE -> "Delete Group"
                }
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                when (currentScreen) {
                    FeedGroupScreen.INITIAL -> {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.name)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { currentScreen = FeedGroupScreen.ICON_PICKER }) {
                                    Icon(painterResource(selectedIcon.getDrawableRes()), contentDescription = null)
                                }
                                Text("Select Icon")
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { currentScreen = FeedGroupScreen.SUBSCRIPTIONS_PICKER }) {
                                Text(stringResource(R.string.feed_group_dialog_select_subscriptions))
                            }
                        }
                    }
                    FeedGroupScreen.ICON_PICKER -> {
                        LazyVerticalGrid(columns = GridCells.Fixed(5)) {
                            items(FeedGroupIcon.entries) { icon ->
                                IconButton(onClick = {
                                    selectedIcon = icon
                                    currentScreen = FeedGroupScreen.INITIAL
                                }) {
                                    Icon(painterResource(icon.getDrawableRes()), contentDescription = null)
                                }
                            }
                        }
                    }
                    FeedGroupScreen.SUBSCRIPTIONS_PICKER -> {
                        subscriptionsData?.let { data ->
                            val items = data.first
                            LazyColumn {
                                items(items) { item ->
                                    val isSelected = selectedSubscriptions.contains(item.uid)
                                    ListItem(
                                        headlineContent = { Text(item.name ?: "Unknown") },
                                        leadingContent = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) selectedSubscriptions.add(item.uid)
                                                    else selectedSubscriptions.remove(item.uid)
                                                }
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            if (isSelected) selectedSubscriptions.remove(item.uid)
                                            else selectedSubscriptions.add(item.uid)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    FeedGroupScreen.DELETE -> {
                        Text(stringResource(R.string.feed_group_dialog_delete_message))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (currentScreen) {
                        FeedGroupScreen.INITIAL -> {
                            if (groupId == -1L) {
                                viewModel.createGroup(name, selectedIcon, selectedSubscriptions.toSet())
                            } else {
                                viewModel.updateGroup(name, selectedIcon, selectedSubscriptions.toSet(), group?.sortOrder ?: -1L)
                            }
                        }
                        FeedGroupScreen.DELETE -> viewModel.deleteGroup()
                        else -> currentScreen = FeedGroupScreen.INITIAL
                    }
                }
            ) {
                Text(
                    when (currentScreen) {
                        FeedGroupScreen.DELETE -> stringResource(R.string.delete)
                        FeedGroupScreen.INITIAL -> if (groupId == -1L) stringResource(R.string.create) else stringResource(R.string.ok)
                        else -> stringResource(R.string.ok)
                    }
                )
            }
        },
        dismissButton = {
            if (currentScreen == FeedGroupScreen.INITIAL && groupId != -1L) {
                TextButton(onClick = { currentScreen = FeedGroupScreen.DELETE }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = {
                if (currentScreen == FeedGroupScreen.INITIAL) onDismiss()
                else currentScreen = FeedGroupScreen.INITIAL
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

enum class FeedGroupScreen {
    INITIAL, ICON_PICKER, SUBSCRIPTIONS_PICKER, DELETE
}
