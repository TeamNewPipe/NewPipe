package org.schabi.newpipe.ui.components.common

import android.net.Uri
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.text.TimestampExtractor

@Composable
fun DescriptionText(
    description: Description,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
    onTimestampClick: ((Int) -> Unit)? = null
) {
    Text(
        modifier = modifier,
        text = rememberParsedDescription(description, onTimestampClick),
        maxLines = maxLines,
        style = style,
        overflow = overflow
    )
}

@Composable
fun rememberParsedDescription(
    description: Description,
    onTimestampClick: ((Int) -> Unit)? = null
): AnnotatedString {
    // TODO: Handle hashtags, Markdown.
    // rememberUpdatedState lets the click handlers inside the AnnotatedString always invoke
    // the latest callback without rebuilding the string on every recomposition.
    val onTimestampClickState = rememberUpdatedState(onTimestampClick)
    val contextState = rememberUpdatedState(LocalContext.current)
    // Use a boolean key so the string is only rebuilt when handler presence changes,
    // not on every lambda identity change.
    val hasTimestampHandler = onTimestampClick != null

    return remember(description, hasTimestampHandler) {
        val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))

        if (description.type == Description.HTML) {
            val baseString = AnnotatedString.fromHtml(description.content, linkStyle)
            if (!hasTimestampHandler) return@remember baseString

            // Rebuild the AnnotatedString, replacing timestamp URL annotations
            // with Clickable ones so they seek the player instead of opening the browser.
            buildAnnotatedString {
                append(baseString.text)
                baseString.spanStyles.forEach { span ->
                    addStyle(span.item, span.start, span.end)
                }
                baseString.paragraphStyles.forEach { para ->
                    addStyle(para.item, para.start, para.end)
                }

                val handledRanges = mutableListOf<IntRange>()

                baseString.getLinkAnnotations(0, baseString.length).forEach { link ->
                    val ann = link.item
                    when (ann) {
                        is LinkAnnotation.Url -> {
                            val secs = getTimestampSecondsFromUrl(ann.url)
                            if (secs != null) {
                                addLink(
                                    clickable = LinkAnnotation.Clickable(
                                        tag = "timestamp",
                                        styles = linkStyle,
                                        linkInteractionListener = {
                                            onTimestampClickState.value?.invoke(secs)
                                        }
                                    ),
                                    start = link.start,
                                    end = link.end
                                )
                                handledRanges += link.start..link.end
                            } else {
                                val url = ann.url
                                addLink(
                                    clickable = LinkAnnotation.Clickable(
                                        tag = "url",
                                        styles = ann.styles,
                                        linkInteractionListener = {
                                            ShareUtils.openUrlInApp(contextState.value, url)
                                        }
                                    ),
                                    start = link.start,
                                    end = link.end
                                )
                            }
                        }

                        is LinkAnnotation.Clickable -> addLink(ann, link.start, link.end)
                    }
                }

                // Also add Clickables for plain-text timestamp patterns not already covered
                val text = baseString.text
                val matcher = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(text)
                while (matcher.find()) {
                    val dto = TimestampExtractor.getTimestampFromMatcher(matcher, text) ?: continue
                    if (handledRanges.none { dto.timestampStart() in it }) {
                        val secs = dto.seconds()
                        addLink(
                            clickable = LinkAnnotation.Clickable(
                                tag = "timestamp",
                                styles = linkStyle,
                                linkInteractionListener = {
                                    onTimestampClickState.value?.invoke(secs)
                                }
                            ),
                            start = dto.timestampStart(),
                            end = dto.timestampEnd()
                        )
                    }
                }
            }
        } else {
            // Plain text: scan for timestamp patterns
            val baseString = AnnotatedString(description.content)
            if (!hasTimestampHandler) return@remember baseString

            val text = baseString.text
            val matcher = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(text)
            if (!matcher.find()) return@remember baseString

            buildAnnotatedString {
                append(baseString)
                matcher.reset()
                while (matcher.find()) {
                    val dto = TimestampExtractor.getTimestampFromMatcher(matcher, text) ?: continue
                    val secs = dto.seconds()
                    addLink(
                        clickable = LinkAnnotation.Clickable(
                            tag = "timestamp",
                            styles = linkStyle,
                            linkInteractionListener = {
                                onTimestampClickState.value?.invoke(secs)
                            }
                        ),
                        start = dto.timestampStart(),
                        end = dto.timestampEnd()
                    )
                }
            }
        }
    }
}

private fun getTimestampSecondsFromUrl(url: String): Int? {
    return try {
        Uri.parse(url).getQueryParameter("t")?.toIntOrNull()
    } catch (_: Exception) {
        null
    }
}
