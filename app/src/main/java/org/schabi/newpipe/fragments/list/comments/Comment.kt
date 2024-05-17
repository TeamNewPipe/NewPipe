package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun Comment(comment: CommentsInfoItem) {
    val context = LocalContext.current

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.padding(all = 8.dp)) {
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

            var isExpanded by rememberSaveable { mutableStateOf(false) }

            Column(
                modifier = Modifier.clickable { isExpanded = !isExpanded },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = comment.uploaderName,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = comment.commentText.content,
                    // If the comment is expanded, we display all its content
                    // otherwise we only display the first two lines
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    style = MaterialTheme.typography.bodyMedium
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

                    if (comment.isPinned) {
                        Image(
                            painter = painterResource(R.drawable.ic_pin),
                            contentDescription = stringResource(R.string.detail_pinned_comment_view_description)
                        )
                    }
                }
            }
        }

        // TODO: Add support for comment replies
    }
}

@Preview(
    name = "Light mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun CommentPreview() {
    val comment = CommentsInfoItem(1, "", "")
    comment.commentText = Description("Hello world!", Description.PLAIN_TEXT)
    comment.uploaderName = "Test"
    comment.likeCount = 100
    comment.isHeartedByUploader = true
    comment.isPinned = true

    AppTheme {
        Comment(comment)
    }
}
