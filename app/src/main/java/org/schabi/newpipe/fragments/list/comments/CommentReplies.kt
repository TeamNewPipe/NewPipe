package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun CommentReplies(
    comment: CommentsInfoItem,
    flow: Flow<PagingData<CommentsInfoItem>>,
    disposables: CompositeDisposable
) {
    val replies = flow.collectAsLazyPagingItems()

    Column {
        CommentRepliesHeader(comment = comment, disposables = disposables)
        HorizontalDivider(thickness = 1.dp)
        LazyColumn {
            items(replies.itemCount) {
                Comment(comment = replies[it]!!)
            }
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
        CommentReplies(comment = comment, flow = flow, disposables = CompositeDisposable())
    }
}
