package org.schabi.newpipe.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.fromHtml
import org.schabi.newpipe.extractor.stream.Description

@Composable
fun rememberParsedDescription(description: Description): AnnotatedString {
    // TODO: Handle links and hashtags, Markdown.
    return remember(description) {
        if (description.type == Description.HTML) {
            AnnotatedString.fromHtml(description.content)
        } else {
            AnnotatedString(description.content, ParagraphStyle())
        }
    }
}
