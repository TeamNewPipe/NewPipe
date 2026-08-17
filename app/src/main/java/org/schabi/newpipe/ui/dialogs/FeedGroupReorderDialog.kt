package org.schabi.newpipe.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

@Composable
fun FeedGroupReorderDialog(
    groups: List<FeedGroupEntity>,
    onReorder: (List<FeedGroupEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    // In a real app, we'd use a reorderable list here.
    // For now, this is a placeholder to get it to build.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_groups_header_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(groups) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        trailingContent = { Icon(Icons.Default.Menu, contentDescription = null) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onReorder(groups)
                onDismiss()
            }) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
