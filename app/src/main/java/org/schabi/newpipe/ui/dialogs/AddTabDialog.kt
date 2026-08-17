package org.schabi.newpipe.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.tabs.Tab

@Composable
fun AddTabDialog(
    availableTabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tab") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(availableTabs) { tab ->
                    ListItem(
                        headlineContent = { Text(tab.getTabName(androidx.compose.ui.platform.LocalContext.current)) },
                        modifier = Modifier.clickable {
                            onTabSelected(tab)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
