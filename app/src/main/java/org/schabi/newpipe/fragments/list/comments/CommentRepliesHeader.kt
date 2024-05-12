package org.schabi.newpipe.fragments.list.comments

import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.text.TextLinkifier

@Composable
fun CommentRepliesHeader(comment: CommentsInfoItem, disposables: CompositeDisposable) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(all = 8.dp)) {
        Row {
            Row(
                modifier = Modifier
                    .padding(all = 8.dp)
                    .clickable {
                        NavigationHelper.openCommentAuthorIfPresent(
                            context as FragmentActivity,
                            comment
                        )
                    }
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
                        text = Localization.relativeTimeOrTextual(
                            context, comment.uploadDate, comment.textualUploadDate
                        )
                    )
                }
            }

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

@Preview
@Composable
fun CommentRepliesHeaderPreview() {
    val disposables = CompositeDisposable()
    val comment = CommentsInfoItem(1, "", "")
    comment.commentText = Description("Hello world!", Description.PLAIN_TEXT)
    comment.uploaderName = "Test"
    comment.textualUploadDate = "5 months ago"

    CommentRepliesHeader(comment, disposables)
}
