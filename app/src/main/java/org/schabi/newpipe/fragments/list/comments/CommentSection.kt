package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun CommentSection(
    flow: Flow<PagingData<CommentsInfoItem>>,
    parentComment: CommentsInfoItem? = null,
) {
    val replies = flow.collectAsLazyPagingItems()
    val itemCount by remember { derivedStateOf { replies.itemCount } }

    Surface(color = MaterialTheme.colorScheme.background) {
        val refresh = replies.loadState.refresh
        if (itemCount == 0 && refresh !is LoadState.Loading) {
            NoCommentsMessage((refresh as? LoadState.Error)?.error)
        } else {
            val listState = rememberLazyListState()

            LazyColumnScrollbar(state = listState, settings = ScrollbarSettings.Default) {
                LazyColumn(state = listState) {
                    if (parentComment != null) {
                        item {
                            CommentRepliesHeader(comment = parentComment)
                            HorizontalDivider(thickness = 1.dp)
                        }
                    }

                    items(itemCount) {
                        Comment(comment = replies[it]!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoCommentsMessage(error: Throwable?) {
    val message = if (error is CommentsDisabledException) {
        R.string.comments_are_disabled
    } else {
        R.string.no_comments
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "(╯°-°)╯", fontSize = 35.sp)
        Text(text = stringResource(id = message), fontSize = 24.sp)
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
        CommentSection(flow = flowOf(pagingData))
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
        CommentSection(parentComment = comment, flow = flow)
    }
}
