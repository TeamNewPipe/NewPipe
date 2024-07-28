package org.schabi.newpipe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens

@Composable
fun TextAction(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

@Composable
fun NavigationIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
        modifier = Modifier.padding(horizontal = SizeTokens.SpacingExtraSmall)
    )
}

@Composable
fun SearchSuggestionItem(text: String) {
    // TODO: Add more components here to display all the required details of a search suggestion item.
    Text(text = text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Toolbar(
    title: String,
    modifier: Modifier = Modifier,
    hasNavigationIcon: Boolean = true,
    hasSearch: Boolean = false,
    onSearchQueryChange: ((String) -> List<String>)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column {
        TopAppBar(
            title = { Text(text = title) },
            modifier = modifier,
            navigationIcon = { if (hasNavigationIcon) NavigationIcon() },
            actions = {
                actions()
                if (hasSearch) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            painterResource(id = R.drawable.ic_search),
                            contentDescription = stringResource(id = R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )
        if (isSearchActive) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                placeholder = {
                    Text(text = stringResource(id = R.string.search))
                },
                active = true,
                onActiveChange = {
                    isSearchActive = it
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    inputFieldColors = SearchBarDefaults.inputFieldColors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            ) {
                onSearchQueryChange?.invoke(query)?.takeIf { it.isNotEmpty() }
                    ?.map { suggestionText -> SearchSuggestionItem(text = suggestionText) }
                    ?: run {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column {
                                Text(text = "╰(°●°╰)")
                                Text(text = stringResource(id = R.string.search_no_results))
                            }
                        }
                    }
            }
        }
    }
}

@Preview
@Composable
fun ToolbarPreview() {
    AppTheme {
        Toolbar(
            title = "Title",
            hasSearch = true,
            onSearchQueryChange = { emptyList() },
            actions = {
                TextAction(text = "Action1")
                TextAction(text = "Action2")
            }
        )
    }
}
