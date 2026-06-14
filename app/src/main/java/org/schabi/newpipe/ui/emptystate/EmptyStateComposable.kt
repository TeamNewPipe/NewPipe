package org.schabi.newpipe.ui.emptystate

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun EmptyStateComposable(
    spec: EmptyStateSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = spec.emojiText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Text(
            modifier = Modifier
                .padding(top = 6.dp)
                .padding(horizontal = 16.dp),
            text = stringResource(spec.descriptionText),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = Color.WHITE.toLong())
@Composable
fun EmptyStateComposableGenericErrorPreview() {
    AppTheme {
        EmptyStateComposable(
            spec = EmptyStateSpec.GenericError,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = Color.WHITE.toLong())
@Composable
fun EmptyStateComposableNoCommentPreview() {
    AppTheme {
        EmptyStateComposable(
            spec = EmptyStateSpec.NoComments,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
        )
    }
}

enum class EmptyStateSpec(
    val emojiText: String,
    @field:StringRes val descriptionText: Int
) {
    GenericError(
        emojiText = "¯\\_(ツ)_/¯",
        descriptionText = R.string.empty_list_subtitle
    ),
    NoVideos(
        emojiText = "(╯°-°)╯",
        descriptionText = R.string.no_videos
    ),
    NoComments(
        emojiText = "¯\\_(╹x╹)_/¯",
        descriptionText = R.string.no_comments
    ),
    DisabledComments(
        emojiText = "¯\\_(╹x╹)_/¯",
        descriptionText = R.string.comments_are_disabled
    ),
    ErrorLoadingComments(
        emojiText = "¯\\_(╹x╹)_/¯",
        descriptionText = R.string.error_unable_to_load_comments
    ),
    NoSearchResult(
        emojiText = "╰(°●°╰)",
        descriptionText = R.string.search_no_results
    ),
    ContentNotSupported(
        emojiText = "(︶︹︺)",
        descriptionText = R.string.content_not_supported
    ),
    NoBookmarkedPlaylist(
        emojiText = "(╥﹏╥)",
        descriptionText = R.string.no_playlist_bookmarked_yet
    ),
    NoSubscriptionsHint(
        emojiText = "(꩜ᯅ꩜)",
        descriptionText = R.string.import_subscriptions_hint
    ),
    NoSubscriptions(
        emojiText = "(꩜ᯅ꩜)",
        descriptionText = R.string.no_channel_subscribed_yet
    )
}
