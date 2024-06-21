package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun CommentSection(
    flow: Flow<PagingData<CommentsInfoItem>>,
    parentComment: CommentsInfoItem? = null,
) {
    val replies = flow.collectAsLazyPagingItems()

    LazyColumn {
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

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentSectionPreview() {
    val comment1 = CommentsInfoItem(
        commentText = Description("This is a comment", Description.PLAIN_TEXT),
        uploaderName = "Test",
    )
    val comment2 = CommentsInfoItem(
        commentText = Description("This is another comment.<br>This is another line.", Description.HTML),
        uploaderName = "Test 2",
    )
    val flow = flowOf(PagingData.from(listOf(comment1, comment2)))

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

    val reply1 = CommentsInfoItem(
        commentText = Description("This is a reply", Description.PLAIN_TEXT),
        uploaderName = "Test 2",
    )
    val reply2 = CommentsInfoItem(
        commentText = Description("This is another reply.<br>This is another line.", Description.HTML),
        uploaderName = "Test 3",
    )
    val flow = flowOf(PagingData.from(listOf(reply1, reply2)))

    AppTheme {
        CommentSection(parentComment = comment, flow = flow)
    }
}
