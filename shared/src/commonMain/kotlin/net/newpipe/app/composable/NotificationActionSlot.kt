/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.model.NotificationAction
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.notification_action_0_title
import newpipe.shared.generated.resources.notification_action_3_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LABEL_SEPARATOR = " • "

@Composable
fun NotificationActionSlot(
    title: String,
    action: NotificationAction,
    onActionChange: (NotificationAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean? = null,
    onCompactToggle: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListItem(
            modifier = Modifier
                .weight(1f)
                .clickable { showDialog = true },
            headlineContent = { Text(text = title) },
            supportingContent = { Text(text = action.title()) },
            leadingContent = {
                Box(modifier = Modifier.size(24.dp)) {
                    action.icon?.let { icon -> Icon(painter = painterResource(icon), contentDescription = null) }
                }
            }
        )
        compact?.let { checked ->
            Checkbox(
                checked = checked,
                onCheckedChange = { onCompactToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }

    if (showDialog) {
        NotificationActionDialog(
            title = title,
            selectedAction = action,
            onActionSelected = { selected ->
                onActionChange(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun NotificationActionDialog(
    title: String,
    selectedAction: NotificationAction,
    onActionSelected: (NotificationAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                NotificationAction.entries.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = action == selectedAction,
                                role = Role.RadioButton,
                                onClick = { onActionSelected(action) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = action == selectedAction, onClick = null)
                        Text(
                            text = action.title(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )
                        action.icon?.let { icon -> Icon(painter = painterResource(icon), contentDescription = null) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun NotificationAction.title(): String = labels
    .map { label -> stringResource(label) }
    .joinToString(separator = LABEL_SEPARATOR)

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun NotificationActionSlotPreviewWithMultipleLabels() {
    NotificationActionSlot(
        title = stringResource(Res.string.notification_action_0_title),
        action = NotificationAction.SMART_REWIND_PREVIOUS,
        onActionChange = {},
        compact = true
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun NotificationActionSlotPreviewWithSingleLabel() {
    NotificationActionSlot(
        title = stringResource(Res.string.notification_action_3_title),
        action = NotificationAction.REPEAT,
        onActionChange = {},
        compact = false
    )
}
