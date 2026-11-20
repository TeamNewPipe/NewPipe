package org.schabi.newpipe.player.ui

import kotlin.math.roundToInt

/** Bounds a popup to the display without cropping portrait video or changing stored user width. */
internal object PopupVideoSizing {
    data class Size(val width: Int, val height: Int)

    @JvmStatic
    fun fit(
        requestedWidth: Int,
        availableWidth: Int,
        availableHeight: Int,
        minimumWidth: Int,
        videoAspectRatio: Float
    ): Size {
        val maxWidth = availableWidth.coerceAtLeast(1)
        val maxHeight = availableHeight.coerceAtLeast(1)
        val effectiveRatio = videoAspectRatio.takeIf { it.isFinite() && it > 0.1f }
            ?.takeIf { it < 1.0f } ?: (16f / 9f)
        val minWidth = minimumWidth.coerceIn(1, maxWidth)
        val wantedWidth = requestedWidth.coerceIn(minWidth, maxWidth)
        val wantedHeight = (wantedWidth / effectiveRatio).roundToInt().coerceAtLeast(1)
        if (wantedHeight <= maxHeight) {
            return Size(wantedWidth, wantedHeight)
        }
        val reducedWidth = (maxHeight * effectiveRatio).roundToInt().coerceIn(1, wantedWidth)
        return Size(reducedWidth, (reducedWidth / effectiveRatio).roundToInt().coerceIn(1, maxHeight))
    }
}
