package org.schabi.newpipe.ui.components.video.comment

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.LoadingIndicator
import org.schabi.newpipe.ui.components.common.NoItemsMessage
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.viewmodels.CommentsViewModel
import org.schabi.newpipe.viewmodels.util.Resource

@Composable
fun CommentSection(commentsViewModel: CommentsViewModel = viewModel()) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val state by commentsViewModel.uiState.collectAsStateWithLifecycle()
        CommentSection(state, commentsViewModel.comments)
    }
}

@Composable
private fun CommentSection(
    uiState: Resource<CommentInfo>,
    commentsFlow: Flow<PagingData<CommentsInfoItem>>
) {
    when (uiState) {
        is Resource.Loading -> LoadingIndicator(modifier = Modifier.padding(top = 8.dp))

        is Resource.Success -> {
            val commentsInfo = uiState.data
            CommentSection(
                commentsFlow = commentsFlow,
                commentCount = commentsInfo.commentCount,
                isCommentsDisabled = commentsInfo.isCommentsDisabled
            )
        }

        is Resource.Error -> {
            // This is not rendered as VideoDetailFragment handles errors
        }
    }
}

@Composable
fun CommentSection(
    commentsFlow: Flow<PagingData<CommentsInfoItem>>,
    commentCount: Int,
    parentComment: CommentsInfoItem? = null,
    isCommentsDisabled: Boolean = false,
) {
    val comments = commentsFlow.collectAsLazyPagingItems()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val state = rememberLazyListState()

    LazyColumnScrollbar(state = state) {
        LazyColumn(
            modifier = Modifier.nestedScroll(nestedScrollInterop),
            state = state
        ) {
            if (parentComment != null) {
                item {
                    CommentRepliesHeader(comment = parentComment)
                    HorizontalDivider(thickness = 1.dp)
                }
            }

            if (comments.itemCount == 0) {
                item {
                    val refresh = comments.loadState.refresh
                    if (refresh is LoadState.Loading) {
                        LoadingIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else {
                        val message = if (refresh is LoadState.Error) {
                            R.string.error_unable_to_load_comments
                        } else if (isCommentsDisabled) {
                            R.string.comments_are_disabled
                        } else {
                            R.string.no_comments
                        }
                        NoItemsMessage(message)
                    }
                }
            } else {
                // The number of replies is already shown in the main comment section
                if (parentComment == null) {
                    item {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = pluralStringResource(R.plurals.comments, commentCount, commentCount),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(comments.itemCount) {
                    Comment(comment = comments[it]!!)
                }
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionLoadingPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(uiState = Resource.Loading, commentsFlow = flowOf())
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionSuccessPreview() {
    val comments = listOf(
        CommentsInfoItem(
            commentText = Description(
                "Comment 1\n\nThis line should be hidden by default.",
                Description.PLAIN_TEXT
            ),
            uploaderName = "Test",
            replies = Page(""),
            replyCount = 10
        )
    ) + (2..10).map {
        CommentsInfoItem(
            commentText = Description("Comment $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(
                uiState = Resource.Success(
                    CommentInfo(
                        serviceId = 1, url = "", comments = comments, nextPage = null,
                        commentCount = 10, isCommentsDisabled = false
                    )
                ),
                commentsFlow = flowOf(PagingData.from(comments))
            )
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionErrorPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(uiState = Resource.Error(RuntimeException()), commentsFlow = flowOf())
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
    val replies = (1..10).map {
        CommentsInfoItem(
            commentText = Description("Reply $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }
    val flow = flowOf(PagingData.from(replies))

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentSection(parentComment = comment, commentsFlow = flow, commentCount = 10)
        }
    }
}
