package org.schabi.newpipe.ui.components.video.comment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.viewmodels.LiveChatViewModel

@Composable
fun LiveChatSection(liveChatViewModel: LiveChatViewModel = viewModel()) {
    val liveChatItems by liveChatViewModel.liveChatItems.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val coroutineScope = rememberCoroutineScope()

    // Track whether user is at the top of the list
    val isAtTop by remember { derivedStateOf { state.firstVisibleItemIndex == 0 } }

    // Track how many messages were seen while at the top
    val lastSeenCount = remember { mutableStateOf(0) }
    if (isAtTop && liveChatItems.isNotEmpty()) {
        lastSeenCount.value = liveChatItems.size
    }

    val unreadCount = liveChatItems.size - lastSeenCount.value

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumnThemedScrollbar(state = state) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollInterop),
                state = state
            ) {
                item {
                    Text(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                        text = "Live Chat",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (liveChatItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 128.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                } else {
                    items(liveChatItems.size, key = { liveChatItems[it].commentId }) {
                        Comment(comment = liveChatItems[it]) {}
                    }
                }
            }
        }

        // Floating button to jump to newest messages
        AnimatedVisibility(
            visible = !isAtTop && unreadCount > 0,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        state.scrollToItem(0)
                    }
                }
            ) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to new messages"
                    )
                }
            }
        }
    }
}
