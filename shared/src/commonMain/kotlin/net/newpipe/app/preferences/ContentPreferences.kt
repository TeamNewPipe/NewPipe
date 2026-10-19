/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Content settings screen.
 */

object ContentPreferences {
    const val KEY_APP_LANGUAGE = "app_language_key"
    const val KEY_YOUTUBE_RESTRICTED_MODE_ENABLED = "youtube_restricted_mode_enabled"
    const val KEY_IMAGE_QUALITY = "image_quality_key"

    // Image quality values, matching the legacy app's PreferredImageQuality preference values.
    const val IMAGE_QUALITY_NONE = "image_quality_none"
    const val IMAGE_QUALITY_LOW = "image_quality_low"
    const val IMAGE_QUALITY_MEDIUM = "image_quality_medium"
    const val IMAGE_QUALITY_HIGH = "image_quality_high"

    const val DEFAULT_YOUTUBE_RESTRICTED_MODE_ENABLED = false
    const val DEFAULT_IMAGE_QUALITY = IMAGE_QUALITY_MEDIUM

    /** Image quality options in display order. */
    val IMAGE_QUALITIES = listOf(
        IMAGE_QUALITY_NONE,
        IMAGE_QUALITY_LOW,
        IMAGE_QUALITY_MEDIUM,
        IMAGE_QUALITY_HIGH,
    )
}