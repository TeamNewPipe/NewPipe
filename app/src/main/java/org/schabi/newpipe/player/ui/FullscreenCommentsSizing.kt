package org.schabi.newpipe.player.ui

/** Keeps user-selected comments width within a safe fraction of the video viewport. */
internal object FullscreenCommentsSizing {
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
