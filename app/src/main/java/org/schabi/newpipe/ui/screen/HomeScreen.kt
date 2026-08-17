package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.LoadingIndicator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.components.VideoItemShimmer
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.HomeViewModel
import org.schabi.newpipe.util.ServiceHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories by viewModel.dynamicCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val serviceId = remember { ServiceHelper.getSelectedServiceId(context) }

    LaunchedEffect(Unit) {
        viewModel.loadHome(serviceId, force = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Dynamic Category / Topic Chips (adapts to user's favorite channels and searches)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { 
                        viewModel.loadHome(serviceId, category)
                    },
                    label = { 
                        Text(
                            text = category,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) 
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    ),
                    border = null
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && items.isEmpty()) {
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
                val listState = rememberLazyListState()
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 5
                    }
                }

                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        viewModel.loadMoreItems()
                    }
                }

                val state = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadHome(serviceId, selectedCategory, force = true) },
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    indicator = {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            )
                        } else if (state.distanceFraction > 0f) {
                            LoadingIndicator(
                                progress = { state.distanceFraction },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            )
                        }
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.url }) { item ->
                            VideoItem(
                                item = item,
                                onClick = {
                                    navigator.navigateTo(AppDestination.VideoDetail(url = item.url, title = item.name))
                                }
                            )
                        }

                        if (isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularWavyProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
