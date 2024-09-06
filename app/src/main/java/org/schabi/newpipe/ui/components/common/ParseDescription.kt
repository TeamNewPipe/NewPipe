package org.schabi.newpipe.ui.components.common

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.ui.components.common.link.YouTubeLinkHandler
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.NO_SERVICE_ID

@Composable
fun parseDescription(description: Description, serviceId: Int): AnnotatedString {
    val context = LocalContext.current
    val linkHandler = remember(serviceId) {
        if (serviceId == ServiceList.YouTube.serviceId) {
            YouTubeLinkHandler(context)
        } else {
            null
        }
    }
    val styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))

    return remember(description) {
        when (description.type) {
            Description.HTML -> AnnotatedString.fromHtml(description.content, styles, linkHandler)
            Description.MARKDOWN -> {
                Markwon.builder(context)
                    .usePlugin(LinkifyPlugin.create())
                    .build()
                    .toMarkdown(description.content)
                    .toAnnotatedString(styles, linkHandler)
            }
            else -> AnnotatedString(description.content)
        }
    }
}

private class DescriptionPreviewProvider : PreviewParameterProvider<Description> {
    override val values = sequenceOf(
        Description("This is a description.", Description.PLAIN_TEXT),
        Description("This is a <b>bold description</b>.", Description.HTML),
        Description("This is a [link](https://example.com).", Description.MARKDOWN),
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParseDescriptionPreview(
    @PreviewParameter(DescriptionPreviewProvider::class) description: Description
) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Text(text = parseDescription(description, NO_SERVICE_ID))
        }
    }
}
