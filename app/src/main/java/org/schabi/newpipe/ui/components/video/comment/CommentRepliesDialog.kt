package org.schabi.newpipe.ui.components.video.comment

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.common.NoItemsMessage
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.md_theme_dark_primary

@Composable
fun CommentRepliesDialog(
    parentComment: CommentsInfoItem,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val commentsFlow = remember {
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            CommentsSource(parentComment.serviceId, parentComment.url, parentComment.replies)
        }.flow
            .cachedIn(coroutineScope)
    }

    CommentRepliesDialog(parentComment, commentsFlow, onDismissRequest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentRepliesDialog(
    parentComment: CommentsInfoItem,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>,
    onDismissRequest: () -> Unit,
) {
    val comments = commentsFlow.collectAsLazyPagingItems()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val state = rememberLazyListState()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyColumnScrollbar(
                state = state,
                settings = ScrollbarSettings.Default.copy(
                    thumbSelectedColor = md_theme_dark_primary,
                    thumbUnselectedColor = Color.Red
                )
            ) {
                LazyColumn(
                    modifier = Modifier.nestedScroll(nestedScrollInterop),
                    state = state
                ) {
                    item {
                        CommentRepliesHeader(comment = parentComment)
                        HorizontalDivider(thickness = 1.dp)
                    }

                    if (comments.itemCount == 0) {
                        item {
                            val refresh = comments.loadState.refresh
                            if (refresh is LoadState.Loading) {
                                LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                            } else {
                                val message = if (refresh is LoadState.Error) {
                                    R.string.error_unable_to_load_comments
                                } else {
                                    R.string.no_comments
                                }
                                NoItemsMessage(message)
                            }
                        }
                    } else {
                        items(comments.itemCount) {
                            Comment(comment = comments[it]!!)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentRepliesDialogPreview() {
    val comment = CommentsInfoItem(
        commentText = Description("Hello world!", Description.PLAIN_TEXT),
        uploaderName = "Test",
        likeCount = 100,
        isPinned = true,
        isHeartedByUploader = true
    )
    val replies = (1..10).map {
        CommentsInfoItem(
            commentText = Description("Reply $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }
    val flow = flowOf(PagingData.from(replies))

    AppTheme {
        CommentRepliesDialog(comment, flow, onDismissRequest = {})
    }
}
