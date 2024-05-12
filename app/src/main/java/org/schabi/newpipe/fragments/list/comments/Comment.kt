package org.schabi.newpipe.fragments.list.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun Comment(comment: CommentsInfoItem) {
    val context = LocalContext.current

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
                        NavigationHelper.openCommentAuthorIfPresent(context as FragmentActivity, comment)
                    }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        var isExpanded by rememberSaveable { mutableStateOf(false) }

        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = comment.uploaderName,
                color = MaterialTheme.colors.secondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = comment.commentText.content,
                // If the comment is expanded, we display all its content
                // otherwise we only display the first line
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@Preview
@Composable
fun CommentPreview() {
    val comment = CommentsInfoItem(1, "", "")
    comment.commentText = Description("Hello world!", Description.PLAIN_TEXT)
    comment.uploaderName = "Test"

    Comment(comment)
}
