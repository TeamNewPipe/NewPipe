package org.schabi.newpipe.ui.emptystate

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.errorHint

@Composable
fun EmptyStateComposable(
    spec: EmptyStateSpec,
    modifier: Modifier = Modifier,
) = EmptyStateComposable(
    modifier = spec.modifier(modifier),
    emojiModifier = spec.emojiModifier(),
    emojiText = spec.emojiText(),
    emojiTextStyle = spec.emojiTextStyle(),
    descriptionModifier = spec.descriptionModifier(),
    descriptionText = spec.descriptionText(),
    descriptionTextStyle = spec.descriptionTextStyle(),
    descriptionTextVisibility = spec.descriptionVisibility(),
)

@Composable
private fun EmptyStateComposable(
    modifier: Modifier,
    emojiModifier: Modifier,
    emojiText: String,
    emojiTextStyle: TextStyle,
    descriptionModifier: Modifier,
    descriptionText: String,
    descriptionTextStyle: TextStyle,
    descriptionTextVisibility: Boolean,
) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.errorHint
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = emojiModifier,
                text = emojiText,
                style = emojiTextStyle,
            )

            if (descriptionTextVisibility) {
                Text(
                    modifier = descriptionModifier,
                    text = descriptionText,
                    style = descriptionTextStyle,
                )
            }
        }
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
        EmptyStateComposable(EmptyStateSpec.NoComment)
    }
}

data class EmptyStateSpec(
    val modifier: (Modifier) -> Modifier,
    val emojiModifier: () -> Modifier,
    val emojiText: @Composable () -> String,
    val emojiTextStyle: @Composable () -> TextStyle,
    val descriptionText: @Composable () -> String,
    val descriptionModifier: () -> Modifier,
    val descriptionTextStyle: @Composable () -> TextStyle,
    val descriptionVisibility: () -> Boolean = { true },
) {

    companion object {

        val GenericError =
            EmptyStateSpec(
                modifier = {
                    it
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                },
                emojiModifier = { Modifier },
                emojiText = { "¯\\_(ツ)_/¯" },
                emojiTextStyle = { MaterialTheme.typography.titleLarge },
                descriptionModifier = {
                    Modifier
                        .padding(top = 6.dp)
                        .padding(horizontal = 16.dp)
                },
                descriptionText = { stringResource(id = R.string.empty_list_subtitle) },
                descriptionTextStyle = { MaterialTheme.typography.bodyMedium }
            )

        val NoComment =
            EmptyStateSpec(
                modifier = { it.padding(top = 85.dp) },
                emojiModifier = { Modifier.padding(bottom = 10.dp) },
                emojiText = { "(╯°-°)╯" },
                emojiTextStyle = {
                    LocalTextStyle.current.merge(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 35.sp,
                    )
                },
                descriptionModifier = { Modifier },
                descriptionText = { stringResource(id = R.string.no_comments) },
                descriptionTextStyle = {
                    LocalTextStyle.current.merge(fontSize = 24.sp)
                }
            )

        val NoSearchResult =
            NoComment.copy(
                modifier = { it },
                emojiText = { "╰(°●°╰)" },
                descriptionText = { stringResource(id = R.string.search_no_results) }
            )

        val NoSearchMaxSizeResult =
            NoSearchResult.copy(
                modifier = { it.fillMaxSize() },
            )

        val ContentNotSupported =
            NoComment.copy(
                modifier = { it.padding(top = 90.dp) },
                emojiText = { "(︶︹︺)" },
                emojiTextStyle = { LocalTextStyle.current.merge(fontSize = 45.sp) },
                descriptionModifier = { Modifier.padding(top = 20.dp) },
                descriptionText = { stringResource(id = R.string.content_not_supported) },
                descriptionTextStyle = { LocalTextStyle.current.merge(fontSize = 15.sp) },
                descriptionVisibility = { false },
            )
    }
}
