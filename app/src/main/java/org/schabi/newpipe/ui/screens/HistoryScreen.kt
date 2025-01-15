package org.schabi.newpipe.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.HistoryViewModel
import org.schabi.newpipe.local.history.SortKey
import org.schabi.newpipe.ui.components.items.ItemList

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val historyItems = viewModel.historyItems.collectAsLazyPagingItems()

    ItemList(historyItems, header = {
        HistoryHeader(sortKey, viewModel::updateOrder)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryHeader(
    sortKey: SortKey,
    onSelectItem: (SortKey) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = when (sortKey) {
        SortKey.MOST_PLAYED -> R.string.title_most_played
        SortKey.LAST_PLAYED -> R.string.title_last_played
    }

    ExposedDropdownMenuBox(
        modifier = Modifier.padding(top = 12.dp, start = 12.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            enabled = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            value = stringResource(selected),
            readOnly = true,
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            label = { Text(text = stringResource(R.string.history_sort_label)) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.title_most_played),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectItem(SortKey.MOST_PLAYED)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.title_last_played),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onClick = {
                    expanded = false
                    onSelectItem(SortKey.LAST_PLAYED)
                }
            )
        }
    }
}
