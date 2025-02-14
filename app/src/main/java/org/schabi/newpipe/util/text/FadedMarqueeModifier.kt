package org.schabi.newpipe.util.text

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Note: the values in [basicMarquee] are hardcoded, but feel free to expose them as parameters
 * in case that will be needed in the future.
 *
 * Taken from sample [androidx.compose.foundation.samples.BasicMarqueeWithFadedEdgesSample].
 */
fun Modifier.fadedMarquee(edgeWidth: Dp): Modifier {
    fun ContentDrawScope.drawFadedEdge(leftEdge: Boolean) {
        val edgeWidthPx = edgeWidth.toPx()
        drawRect(
            topLeft = Offset(if (leftEdge) 0f else size.width - edgeWidthPx, 0f),
            size = Size(edgeWidthPx, size.height),
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startX = if (leftEdge) 0f else size.width,
                endX = if (leftEdge) edgeWidthPx else size.width - edgeWidthPx
            ),
            blendMode = BlendMode.DstIn
        )
    }

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawFadedEdge(leftEdge = true)
            drawFadedEdge(leftEdge = false)
        }
        .basicMarquee(
            repeatDelayMillis = 2000,
            // wait some time before starting animations, to not distract the user
            initialDelayMillis = 4000,
            iterations = Int.MAX_VALUE,
            spacing = MarqueeSpacing(edgeWidth)
        )
        .padding(start = edgeWidth)
}
