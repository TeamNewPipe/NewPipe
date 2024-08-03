package org.schabi.newpipe.ui.components.comment

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import org.schabi.newpipe.ui.components.common.DescriptionText
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun CommentRepliesHeader(comment: CommentsInfoItem) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.clickable {
                    val activity = context as FragmentActivity
                    NavigationHelper.openCommentAuthorIfPresent(activity, comment)
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageStrategy.choosePreferredImage(comment.uploaderAvatars),
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.placeholder_person),
                    error = painterResource(R.drawable.placeholder_person),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )

                Column {
                    Text(text = comment.uploaderName)

                    Text(
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        text = Localization.relativeTimeOrTextual(
                            context, comment.uploadDate, comment.textualUploadDate
                        )
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                if (comment.isPinned) {
                    Image(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.detail_pinned_comment_view_description)
                    )
                }
            }
        }

        DescriptionText(
            description = comment.commentText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CommentRepliesHeaderPreview() {
    val comment = CommentsInfoItem(
        commentText = Description("Hello world!", Description.PLAIN_TEXT),
        uploaderName = "Test",
        likeCount = 1000,
        isPinned = true,
        isHeartedByUploader = true
    )

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CommentRepliesHeader(comment)
        }
    }
}
