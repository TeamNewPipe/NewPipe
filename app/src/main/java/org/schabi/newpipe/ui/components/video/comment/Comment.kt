package org.schabi.newpipe.ui.components.video.comment

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.rememberParsedDescription
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.copyToClipboardCallback
import org.schabi.newpipe.util.image.ImageStrategy

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Comment(
    comment: CommentsInfoItem,
    uploaderAvatarUrl: String? = null,
    onCommentAuthorOpened: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showReplies by rememberSaveable { mutableStateOf(false) }
    val parsedDescription = rememberParsedDescription(comment.commentText)

    Row(
        modifier = Modifier
            .animateContentSize()
            .combinedClickable(
                onLongClick = copyToClipboardCallback { parsedDescription },
                onClick = { isExpanded = !isExpanded },
            )
            .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(comment.uploaderAvatars),
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder_person),
            error = painterResource(R.drawable.placeholder_person),
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(42.dp)
                .clip(CircleShape)
                .clickable {
                    NavigationHelper.openCommentAuthorIfPresent(context, comment)
                    onCommentAuthorOpened?.invoke()
                }
        )

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.detail_pinned_comment_view_description),
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(20.dp)
                    )
                }

                val nameAndDate = remember(comment) {
                    Localization.concatenateStrings(
                        Localization.localizeUserName(comment.uploaderName),
                        Localization.relativeTimeOrTextual(
                            context, comment.uploadDate, comment.textualUploadDate
                        )
                    )
                }
                Text(
                    text = nameAndDate,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = parsedDescription,
                // If the comment is expanded, we display all its content
                // otherwise we only display the first two lines
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 1.dp, top = 6.dp, end = 4.dp, bottom = 6.dp)
                ) {
                    // do not show anything if the like count is unknown
                    if (comment.likeCount >= 0) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.detail_likes_img_view_description),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(20.dp),
                        )
                        Text(
                            text = Localization.likeCount(context, comment.likeCount),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    if (comment.isHeartedByUploader) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.detail_heart_img_view_description),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (comment.replies != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (comment.hasCreatorReply()) {
                            AsyncImage(
                                model = uploaderAvatarUrl,
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.placeholder_person),
                                error = painterResource(R.drawable.placeholder_person),
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                            )
                        }

                        // reduce LocalMinimumInteractiveComponentSize from 48dp to 44dp to slightly
                        // reduce the button margin (which is still clickable but not visible)
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 44.dp) {
                            TextButton(
                                onClick = { showReplies = true },
                                modifier = Modifier.padding(end = 2.dp)
                            ) {
                                val text = pluralStringResource(
                                    R.plurals.replies, comment.replyCount, comment.replyCount.toString()
                                )
                                Text(text = text)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReplies) {
        CommentRepliesDialog(
            parentComment = comment,
            onDismissRequest = { showReplies = false },
            onCommentAuthorOpened = onCommentAuthorOpened,
        )
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
    hasCreatorReply: Boolean = false,
) = CommentsInfoItem(serviceId, url, name).apply {
    this.commentText = commentText
    this.uploaderName = uploaderName
    this.textualUploadDate = textualUploadDate
    this.likeCount = likeCount
    this.isHeartedByUploader = isHeartedByUploader
    this.isPinned = isPinned
    this.replies = replies
    this.replyCount = replyCount
    setCreatorReply(hasCreatorReply)
}

private class CommentPreviewProvider : CollectionPreviewParameterProvider<CommentsInfoItem>(
    listOf(
        CommentsInfoItem(
            commentText = Description("Hello world!\n\nThis line should be hidden by default.", Description.PLAIN_TEXT),
            uploaderName = "Test",
            likeCount = 100,
            isPinned = false,
            isHeartedByUploader = true,
            replies = null,
            replyCount = 0
        ),
        CommentsInfoItem(
            commentText = Description("Hello world, long long long text lorem ipsum dolor sit amet!<br><br>This line should be hidden by default.", Description.HTML),
            uploaderName = "Test",
            likeCount = 92847,
            isPinned = true,
            isHeartedByUploader = false,
            replies = Page(""),
            replyCount = 10
        ),
        CommentsInfoItem(
            commentText = Description("Hello world, long long long text lorem ipsum dolor sit amet!<br><br>This line should be hidden by default.", Description.HTML),
            uploaderName = "Test really long long long long lorem ipsum dolor sit amet consectetur",
            likeCount = 92847,
            isPinned = true,
            isHeartedByUploader = true,
            replies = null,
            replyCount = 0
        ),
        CommentsInfoItem(
            commentText = Description("Short comment", Description.HTML),
            uploaderName = "Test really long long long long lorem ipsum dolor sit amet consectetur",
            likeCount = 92847,
            isPinned = false,
            isHeartedByUploader = false,
            replies = Page(""),
            replyCount = 4283,
            hasCreatorReply = true,
        ),
    )
)

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentPreview(
    @PreviewParameter(CommentPreviewProvider::class) commentsInfoItem: CommentsInfoItem
) {
    AppTheme {
        Surface {
            Comment(commentsInfoItem)
        }
    }
}

@Preview
@Composable
private fun CommentListPreview() {
    AppTheme {
        Surface {
            Column {
                for (comment in CommentPreviewProvider().values) {
                    Comment(comment)
                }
            }
        }
    }
}
