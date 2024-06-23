package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun CommentSection(
    flow: Flow<PagingData<CommentsInfoItem>>,
    parentComment: CommentsInfoItem? = null,
) {
    val replies = flow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    LazyColumnScrollbar(state = listState, settings = ScrollbarSettings.Default) {
        LazyColumn(state = listState) {
            if (parentComment != null) {
                item {
                    CommentRepliesHeader(comment = parentComment)
                    HorizontalDivider(thickness = 1.dp)
                }
            }

            items(replies.itemCount) {
                Comment(comment = replies[it]!!)
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionPreview() {
    val comments = (1..100).map {
        CommentsInfoItem(
            commentText = Description("Comment $it", Description.PLAIN_TEXT),
            uploaderName = "Test"
        )
    }
    val flow = flowOf(PagingData.from(comments))

    AppTheme {
        CommentSection(flow = flow)
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
