package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.components.VideoItemShimmer
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.SearchViewModel
import org.schabi.newpipe.util.ServiceHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    var searchQuery by remember { mutableStateOf(query) }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            searchQuery = query
            viewModel.onQueryChange(query)
            viewModel.search(ServiceHelper.getSelectedServiceId(context), query)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                viewModel.onQueryChange(it)
            },
            onSearch = {
                active = false
                viewModel.search(ServiceHelper.getSelectedServiceId(context), searchQuery)
            },
            active = active,
            onActiveChange = { active = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (active) 0.dp else 16.dp, vertical = 8.dp),
            placeholder = { Text("Search YouTube") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (active && searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        ) {
            // YouTube-style Search History & Suggestions List
            if (searchHistory.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(searchHistory, key = { it }) { historyQuery ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Search History",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = historyQuery,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Insert query into search bar (like YouTube)
                                    IconButton(
                                        onClick = {
                                            searchQuery = historyQuery
                                            viewModel.onQueryChange(historyQuery)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NorthWest,
                                            contentDescription = "Insert search query",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Delete item from history one-by-one (like YouTube)
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteSearchHistoryItem(historyQuery)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = historyQuery
                                    viewModel.onQueryChange(historyQuery)
                                    active = false
                                    viewModel.search(ServiceHelper.getSelectedServiceId(context), historyQuery)
                                }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No search history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && results.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(5) {
                        VideoItemShimmer()
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { item ->
                        VideoItem(
                            item = item,
                            onClick = {
                                navigator.navigateTo(AppDestination.VideoDetail(url = item.url, title = item.name))
                            }
                        )
                    }
                }
            }
        }
    }
}

