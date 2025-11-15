/*
 * SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.components.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldWithToolbar(
    title: String,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    hasSearch: Boolean = false,
    onSearchQueryChange: ((String) -> List<String>)? = null,
    onSearchAction: ((String) -> Unit)? = null,
    searchPlaceholder: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = {
                                onSearchAction?.invoke(it)
                                isSearchActive = false
                            },
                            expanded = true,
                            onExpandedChange = { isSearchActive = it },
                            placeholder = searchPlaceholder ?: {
                                Text(stringResource(id = R.string.search))
                            },
                            leadingIcon = {
                                IconButton(onClick = {
                                    isSearchActive = false
                                    query = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.navigate_back)
                                    )
                                }
                            }
                        )
                    },
                    expanded = true,
                    onExpandedChange = { isSearchActive = it },
                ) {
                    val suggestions = onSearchQueryChange?.invoke(query) ?: emptyList()
                    if (suggestions.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth()) {
                            suggestions.forEach { suggestionText ->
                                SearchSuggestionItem(text = suggestionText)
                            }
                        }
                    } else {
                            DefaultSearchNoResults()
                    }
                }
            } else {
                TopAppBar(
                    title = { Text(text = title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    actions = {
                        actions()
                        // existing actions
                        if (hasSearch) {
                            // Show search icon
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_search),
                                    contentDescription = stringResource(id = R.string.search),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                )
            }
        },
        content = content
    )
}

@Composable
fun SearchSuggestionItem(text: String) {
    // TODO: Add more components here to display all the required details of a search suggestion item.
    Text(text = text)
}

@Composable
private fun DefaultSearchNoResults() {
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

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScaffoldWithToolbarPreview() {
    AppTheme {
        ScaffoldWithToolbar(
            title = "Example",
            onBackClick = {},
            hasSearch = true,
            onSearchQueryChange = { query ->
                if (query.isNotBlank()) {
                    listOf("Suggestion 1 for $query", "Suggestion 2 for $query")
                } else {
                    emptyList()
                }
            },
            onSearchAction = { query ->
                println("Searching for: $query")
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Screen Content")
                }
            }
        )
    }
}
