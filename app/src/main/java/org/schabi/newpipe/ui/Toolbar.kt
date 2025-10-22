package org.schabi.newpipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraSmall

@Composable
fun TextAction(text: String, modifier: Modifier = Modifier) {
    Text(text = text, color = MaterialTheme.colorScheme.onPrimary, modifier = modifier)
}

@Composable
fun NavigationIcon(navigateBack: () -> Unit) {
    IconButton(onClick = navigateBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.padding(horizontal = SizeTokens.SpacingExtraSmall)
        )
    }
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
    onNavigateBack: (() -> Unit)? = null,
    hasSearch: Boolean = false,
    onSearch: (String) -> Unit,
    searchResults: List<String>,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val textFieldState = rememberTextFieldState()

    Column {
        TopAppBar(
            title = { Text(text = title) },
            modifier = modifier,
            navigationIcon = {
                onNavigateBack?.let { NavigationIcon(onNavigateBack) }
            },
            actions = {
                actions()
                if (hasSearch) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            painterResource(id = R.drawable.ic_search),
                            contentDescription = stringResource(id = R.string.search),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        )
        if (isSearchActive) {
            Box(
                modifier
                    .fillMaxSize()
                    .semantics { isTraversalGroup = true }
            ) {
                SearchBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .semantics { traversalIndex = 0f },
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = textFieldState.text.toString(),
                            onQueryChange = { textFieldState.edit { replace(0, length, it) } },
                            onSearch = {
                                onSearch(textFieldState.text.toString())
                                expanded = false
                            },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text(text = stringResource(id = R.string.search)) },
                            modifier = Modifier.padding(horizontal = SpacingExtraSmall)
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(SpacingExtraSmall),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column {
                                Text(text = "╰(°●°╰)")
                                Text(text = stringResource(id = R.string.search_no_results))
                            }
                        }
                    } else {
                        LazyColumn {
                            items(searchResults) { result ->
                                ListItem(
                                    headlineContent = { SearchSuggestionItem(result) },
                                    modifier = Modifier
                                        .clickable {
                                            textFieldState.edit { replace(0, length, result) }
                                            expanded = false
                                        }
                                        .fillMaxWidth()
                                )
                            }
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
            onSearch = {},
            searchResults = emptyList(),
            actions = {
                TextAction(text = "Action1")
                TextAction(text = "Action2")
            }
        )
    }
}
