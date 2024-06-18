package org.schabi.newpipe.fragments.list.comments

import android.content.res.Configuration
import android.widget.TextView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.text.TextLinkifier

@Composable
fun CommentRepliesHeader(comment: CommentsInfoItem, disposables: CompositeDisposable) {
    val context = LocalContext.current

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(all = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                        .clickable {
                            NavigationHelper.openCommentAuthorIfPresent(
                                context as FragmentActivity,
                                comment
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
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
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        movementMethod = LinkMovementMethodCompat.getInstance()
                    }
                },
                update = { view ->
                    // setup comment content
                    TextLinkifier.fromDescription(
                        view, comment.commentText, HtmlCompat.FROM_HTML_MODE_LEGACY,
                        ServiceHelper.getServiceById(comment.serviceId), comment.url, disposables,
                        null
                    )
                }
            )
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CommentRepliesHeaderPreview() {
    val disposables = CompositeDisposable()
    val comment = CommentsInfoItem(1, "", "")
    comment.commentText = Description("Hello world!", Description.PLAIN_TEXT)
    comment.uploaderName = "Test"
    comment.textualUploadDate = "5 months ago"
    comment.likeCount = 100
    comment.isPinned = true
    comment.isHeartedByUploader = true

    AppTheme {
        CommentRepliesHeader(comment, disposables)
    }
}
