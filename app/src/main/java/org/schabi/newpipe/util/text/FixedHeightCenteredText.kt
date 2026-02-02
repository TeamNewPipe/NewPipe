package org.schabi.newpipe.util.text

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Like [Text] but with a fixed bounding box of [lines] text lines, and with text always centered
 * within it even when its actual length uses less than [lines] lines.
 */
@Composable
fun FixedHeightCenteredText(
    text: String,
    lines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    Box(modifier = modifier) {
        // this allows making the box always the same height (i.e. the height of [lines] text
        // lines), while making the text appear centered if it is just a single line
        Text(
            text = "",
            style = style,
            minLines = lines
        )
        Text(
            text = text,
            style = style,
            maxLines = lines,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
