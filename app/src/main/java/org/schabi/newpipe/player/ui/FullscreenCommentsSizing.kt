package org.schabi.newpipe.player.ui

/** Keeps user-selected comments width within a safe fraction of the video viewport. */
internal object FullscreenCommentsSizing {
    fun useBottomPanel(width: Int, height: Int, density: Float, tv: Boolean): Boolean = !tv && height > width && width < 600 * density

    fun height(available: Int, minimumVideoHeight: Int, selectedRatio: Float?): Int {
        if (available <= 0) return 1
        val upper = (available * 0.65f).toInt().coerceAtLeast(1)
        val lower = (available * 0.25f).toInt().coerceAtLeast(1)
        val maximum = (available - minimumVideoHeight).coerceIn(lower, upper)
        val minimum = (available * 0.3f).toInt().coerceIn(1, maximum)
        val ratio = selectedRatio?.takeIf(Float::isFinite) ?: 0.45f
        return (available * ratio).toInt().coerceIn(minimum, maximum)
    }

    fun clampWidth(requested: Int, available: Int): Int {
        if (available <= 0) return 1
        val minWidth = (available * 0.25f).toInt().coerceAtLeast(1)
        val maxWidth = (available * 0.65f).toInt().coerceAtLeast(minWidth)
        return requested.coerceIn(minWidth, maxWidth)
    }

    fun width(available: Int, defaultWidth: Int, selectedRatio: Float?): Int {
        if (available <= 0) return 1
        if (selectedRatio == null || !selectedRatio.isFinite()) {
            return defaultWidth.coerceAtMost((available * 0.45f).toInt()).coerceAtLeast(1)
        }
        return clampWidth((available * selectedRatio).toInt(), available)
    }
}
