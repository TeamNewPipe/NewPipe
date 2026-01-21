package org.schabi.newpipe.ui.components.metadata

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun ImageMetadataItem(@StringRes title: Int, images: List<Image>) {
    val context = LocalContext.current
    val imageLinks = remember(images) { convertImagesToLinks(context, images) }

    MetadataItem(title = title, value = imageLinks)
}

fun LazyListScope.imageMetadataItem(@StringRes title: Int, images: List<Image>) {
    if (images.isNotEmpty()) {
        item {
            ImageMetadataItem(title, images)
        }
    }
}

private fun convertImagesToLinks(context: Context, images: List<Image>): AnnotatedString {
    val preferredUrl = ImageStrategy.choosePreferredImage(images)

    fun imageSizeToText(size: Int): String {
        return if (size == Image.HEIGHT_UNKNOWN) context.getString(R.string.question_mark)
        else size.toString()
    }

    return buildAnnotatedString {
        for (image in images) {
            if (length != 0) {
                append(", ")
            }

            val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Url(image.url, linkStyle)) {
                val weight = if (image.url == preferredUrl) FontWeight.Bold else FontWeight.Normal

                withStyle(SpanStyle(fontWeight = weight)) {
                    // if even the resolution level is unknown, ?x? will be shown
                    if (image.height != Image.HEIGHT_UNKNOWN || image.width != Image.WIDTH_UNKNOWN ||
                        image.estimatedResolutionLevel == ResolutionLevel.UNKNOWN
                    ) {
                        append("${imageSizeToText(image.width)}x${imageSizeToText(image.height)}")
                    } else if (image.estimatedResolutionLevel == ResolutionLevel.LOW) {
                        append(context.getString(R.string.image_quality_low))
                    } else if (image.estimatedResolutionLevel == ResolutionLevel.MEDIUM) {
                        append(context.getString(R.string.image_quality_medium))
                    } else if (image.estimatedResolutionLevel == ResolutionLevel.HIGH) {
                        append(context.getString(R.string.image_quality_high))
                    }
                }
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageMetadataItemPreview() {
    val images = listOf(
        Image("https://example.com/image_low.png", 16, 16, ResolutionLevel.LOW),
        Image("https://example.com/image_mid.png", 32, 32, ResolutionLevel.MEDIUM)
    )

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ImageMetadataItem(
                title = R.string.metadata_uploader_avatars,
                images = images
            )
        }
    }
}
