package org.schabi.newpipe.ui.components.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.schabi.newpipe.extractor.stream.Description

@Composable
fun DescriptionText(
    description: Description,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    // TODO: Handle links and hashtags, Markdown.
    val parsedDescription = remember(description) {
        if (description.type == Description.HTML) {
            val styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
            AnnotatedString.fromHtml(description.content, styles)
        } else {
            AnnotatedString(description.content, ParagraphStyle())
        }
    }

    Text(
        modifier = modifier,
        text = parsedDescription,
        maxLines = maxLines,
        style = style,
        overflow = overflow
    )
}
