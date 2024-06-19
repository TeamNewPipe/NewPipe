package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun rememberParsedText(commentText: Description): AnnotatedString {
    // TODO: Handle links and hashtags, Markdown.
    return remember(commentText) {
        if (commentText.type == Description.HTML) {
            AnnotatedString.fromHtml(commentText.content)
        } else {
            AnnotatedString(commentText.content, ParagraphStyle())
        }
    }
}

@Composable
fun Comment(comment: CommentsInfoItem) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(all = 8.dp)
        ) {
            if (ImageStrategy.shouldLoadImages()) {
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
                                context as FragmentActivity,
                                comment
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

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

                Text(
                    text = rememberParsedText(comment.commentText),
                    // If the comment is expanded, we display all its content
                    // otherwise we only display the first two lines
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_thumb_up),
                        contentDescription = stringResource(R.string.detail_likes_img_view_description)
                    )
                    Text(text = comment.likeCount.toString())

                    if (comment.isHeartedByUploader) {
                        Image(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = stringResource(R.string.detail_heart_img_view_description)
                        )
                    }
                }
            }
        }

        // TODO: Add support for comment replies
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
) = CommentsInfoItem(serviceId, url, name).apply {
    this.commentText = commentText
    this.uploaderName = uploaderName
    this.textualUploadDate = textualUploadDate
    this.likeCount = likeCount
    this.isHeartedByUploader = isHeartedByUploader
    this.isPinned = isPinned
}

class DescriptionPreviewProvider : PreviewParameterProvider<Description> {
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
        isHeartedByUploader = true
    )

    AppTheme {
        Comment(comment)
    }
}
