/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.stream

import java.io.Serializable

/** Relative replay intensity within one video, over an interval measured in milliseconds. */
data class ReplayHeatmapSample(val startTimeMs: Long, val endTimeMs: Long, val intensity: Float) : Serializable {
    init {
        require(startTimeMs >= 0 && endTimeMs > startTimeMs)
        require(intensity.isFinite() && intensity in 0f..1f)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
