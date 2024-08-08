package org.schabi.newpipe.ui.components.comment

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.ui.components.common.DescriptionText
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Comment(comment: CommentsInfoItem) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showReplies by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .animateContentSize()
            .clickable { isExpanded = !isExpanded }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(comment.uploaderAvatars),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_person),
            error = painterResource(R.drawable.placeholder_person),
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable {
                    NavigationHelper.openCommentAuthorIfPresent(
                        context as FragmentActivity, comment
                    )
                }
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (comment.isPinned) {
                    Image(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.detail_pinned_comment_view_description)
                    )
                }

                val nameAndDate = remember(comment) {
                    val date = Localization.relativeTimeOrTextual(
                        context, comment.uploadDate, comment.textualUploadDate
                    )
                    Localization.concatenateStrings(comment.uploaderName, date)
                }
                Text(text = nameAndDate, color = MaterialTheme.colorScheme.secondary)
            }

            DescriptionText(
                description = comment.commentText,
                // If the comment is expanded, we display all its content
                // otherwise we only display the first two lines
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_thumb_up),
                        contentDescription = stringResource(R.string.detail_likes_img_view_description)
                    )
                    Text(text = Localization.likeCount(context, comment.likeCount))

                    if (comment.isHeartedByUploader) {
                        Image(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = stringResource(R.string.detail_heart_img_view_description)
                        )
                    }
                }

                if (comment.replies != null) {
                    TextButton(onClick = { showReplies = true }) {
                        val text = pluralStringResource(
                            R.plurals.replies, comment.replyCount, comment.replyCount.toString()
                        )
                        Text(text = text)
                    }
                }
            }
        }
    }

    if (showReplies) {
        ModalBottomSheet(onDismissRequest = { showReplies = false }) {
            val coroutineScope = rememberCoroutineScope()
            val flow = remember {
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    CommentsSource(comment.serviceId, comment.url, comment.replies)
                }.flow
                    .cachedIn(coroutineScope)
            }

            CommentSection(parentComment = comment, commentsFlow = flow)
        }
    }
}

fun CommentsInfoItem(
    serviceId: Int = 1,
    url: String = "",
    name: String = "",
    commentText: Description,
    uploaderName: String,
    textualUploadDate: String = "5 months ago",
    likeCount: Int = 0,
    isHeartedByUploader: Boolean = false,
    isPinned: Boolean = false,
    replies: Page? = null,
    replyCount: Int = 0,
) = CommentsInfoItem(serviceId, url, name).apply {
    this.commentText = commentText
    this.uploaderName = uploaderName
    this.textualUploadDate = textualUploadDate
    this.likeCount = likeCount
    this.isHeartedByUploader = isHeartedByUploader
    this.isPinned = isPinned
    this.replies = replies
    this.replyCount = replyCount
}

private class DescriptionPreviewProvider : PreviewParameterProvider<Description> {
    override val values = sequenceOf(
        Description("Hello world!<br><br>This line should be hidden by default.", Description.HTML),
        Description("Hello world!\n\nThis line should be hidden by default.", Description.PLAIN_TEXT),
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentPreview(
    @PreviewParameter(DescriptionPreviewProvider::class) description: Description
) {
    val comment = CommentsInfoItem(
        commentText = description,
        uploaderName = "Test",
        likeCount = 100,
        isPinned = true,
        isHeartedByUploader = true,
        replies = Page(""),
        replyCount = 10
    )

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Comment(comment)
        }
    }
}
