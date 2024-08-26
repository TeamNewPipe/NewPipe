package org.schabi.newpipe.ui.components.comment

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.paging.CommentsDisabledException
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.common.NoItemsMessage
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.viewmodels.CommentsViewModel

@Composable
fun CommentSection(commentsViewModel: CommentsViewModel = viewModel()) {
    CommentSection(commentsFlow = commentsViewModel.comments)
}

@Composable
fun CommentSection(
    parentComment: CommentsInfoItem? = null,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>
) {
    val comments = commentsFlow.collectAsLazyPagingItems()
    val itemCount by remember { derivedStateOf { comments.itemCount } }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val state = rememberLazyListState()

    LazyColumnScrollbar(state = state) {
        LazyColumn(modifier = Modifier.nestedScroll(nestedScrollInterop), state = state) {
            if (parentComment != null) {
                item {
                    CommentRepliesHeader(comment = parentComment)
                    HorizontalDivider(thickness = 1.dp)
                }
            }

            if (itemCount == 0) {
                item {
                    val refresh = comments.loadState.refresh
                    if (refresh is LoadState.Loading) {
                        LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else {
                        val error = (refresh as? LoadState.Error)?.error
                        val message = if (error is CommentsDisabledException) {
                            R.string.comments_are_disabled
                        } else {
                            R.string.no_comments
                        }
                        NoItemsMessage(message)
                    }
                }
            } else {
                items(itemCount) {
                    Comment(comment = comments[it]!!)
                }
            }
        }
    }
}

private class CommentDataProvider : PreviewParameterProvider<PagingData<CommentsInfoItem>> {
    private val notLoading = LoadState.NotLoading(true)

    override val values = sequenceOf(
        // Normal view
        PagingData.from(
            (1..100).map {
                CommentsInfoItem(
                    commentText = Description("Comment $it", Description.PLAIN_TEXT),
                    uploaderName = "Test"
                )
            }
        ),
        // Comments disabled
        PagingData.from(
            listOf<CommentsInfoItem>(),
            LoadStates(LoadState.Error(CommentsDisabledException()), notLoading, notLoading)
        ),
        // No comments
        PagingData.from(
            listOf<CommentsInfoItem>(),
            LoadStates(notLoading, notLoading, notLoading)
        )
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionPreview(
    @PreviewParameter(CommentDataProvider::class) pagingData: PagingData<CommentsInfoItem>
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(commentsFlow = flowOf(pagingData))
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentRepliesPreview() {
    val comment = CommentsInfoItem(
        commentText = Description("Hello world!", Description.PLAIN_TEXT),
        uploaderName = "Test",
        likeCount = 100,
        isPinned = true,
        isHeartedByUploader = true
    )
    val replies = (1..100).map {
        CommentsInfoItem(
            commentText = Description("Reply $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }
    val flow = flowOf(PagingData.from(replies))

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(parentComment = comment, commentsFlow = flow)
        }
    }
}