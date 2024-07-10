package org.schabi.newpipe.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import org.schabi.newpipe.extractor.stream.Description

@Composable
fun rememberParsedDescription(description: Description): AnnotatedString {
    // TODO: Handle links and hashtags, Markdown.
    return remember(description) {
        if (description.type == Description.HTML) {
            val styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
            AnnotatedString.fromHtml(description.content, styles)
        } else {
            AnnotatedString(description.content, ParagraphStyle())
        }
    }
}
