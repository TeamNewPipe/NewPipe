package org.schabi.newpipe.ui.emptystate

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
    modifier: Modifier = Modifier,
) = EmptyStateComposable(
    modifier = spec.modifier(modifier),
    emojiText = spec.emojiText(),
    descriptionText = spec.descriptionText(),
)

@Composable
private fun EmptyStateComposable(
    emojiText: String,
    descriptionText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emojiText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Text(
            modifier = Modifier
                .padding(top = 6.dp)
                .padding(horizontal = 16.dp),
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = Color.WHITE.toLong())
@Composable
fun EmptyStateComposableGenericErrorPreview() {
    AppTheme {
        EmptyStateComposable(EmptyStateSpec.GenericError)
    }
}

@Preview(showBackground = true, backgroundColor = Color.WHITE.toLong())
@Composable
fun EmptyStateComposableNoCommentPreview() {
    AppTheme {
        EmptyStateComposable(EmptyStateSpec.NoComments)
    }
}

data class EmptyStateSpec(
    val modifier: (Modifier) -> Modifier,
    val emojiText: @Composable () -> String,
    val descriptionText: @Composable () -> String,
) {
    companion object {

        val GenericError =
            EmptyStateSpec(
                modifier = {
                    it
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                },
                emojiText = { "¯\\_(ツ)_/¯" },
                descriptionText = { stringResource(id = R.string.empty_list_subtitle) },
            )

        val NoVideos =
            EmptyStateSpec(
                modifier = {
                    it
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                },
                emojiText = { "(╯°-°)╯" },
                descriptionText = { stringResource(id = R.string.no_videos) },
            )

        val NoComments =
            EmptyStateSpec(
                modifier = {
                    it
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                },
                emojiText = { "¯\\_(╹x╹)_/¯" },
                descriptionText = { stringResource(id = R.string.no_comments) },
            )

        val DisabledComments =
            NoComments.copy(
                descriptionText = { stringResource(id = R.string.comments_are_disabled) },
            )

        val NoSearchResult =
            NoComments.copy(
                modifier = { it },
                emojiText = { "╰(°●°╰)" },
                descriptionText = { stringResource(id = R.string.search_no_results) }
            )

        val NoSearchMaxSizeResult =
            NoSearchResult.copy(
                modifier = { it.fillMaxSize() },
            )

        val ContentNotSupported =
            NoComments.copy(
                modifier = { it.padding(top = 90.dp) },
                emojiText = { "(︶︹︺)" },
                descriptionText = { stringResource(id = R.string.content_not_supported) },
            )

        val NoBookmarkedPlaylist =
            EmptyStateSpec(
                modifier = { it },
                emojiText = { "(╥﹏╥)" },
                descriptionText = { stringResource(id = R.string.no_playlist_bookmarked_yet) },
            )

        val NoSubscriptionsHint =
            EmptyStateSpec(
                modifier = { it },
                emojiText = { "(꩜ᯅ꩜)" },
                descriptionText = { stringResource(id = R.string.import_subscriptions_hint) },
            )

        val NoSubscriptions =
            NoSubscriptionsHint.copy(
                descriptionText = { stringResource(id = R.string.no_channel_subscribed_yet) },
            )
    }
}
