/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Content settings screen
 */
object ContentPreferences {
    const val KEY_APP_LANGUAGE = "app_language_key"
    const val KEY_CONTENT_LANGUAGE = "content_language"
    const val KEY_CONTENT_COUNTRY = "content_country"
    const val KEY_CHANNEL_TABS = "channel_tabs"
    const val KEY_SHOW_AGE_RESTRICTED_CONTENT = "show_age_restricted_content"
    const val KEY_YOUTUBE_RESTRICTED_MODE = "youtube_restricted_mode_enabled"
    const val KEY_SEARCH_SUGGESTIONS = "show_search_suggestions"
    const val KEY_IMAGE_QUALITY = "image_quality_key"
    const val KEY_SHOW_COMMENTS = "show_comments"
    const val KEY_SHOW_NEXT_VIDEO = "show_next_video"
    const val KEY_SHOW_DESCRIPTION = "show_description"
    const val KEY_SHOW_META_INFO = "show_meta_info"
    const val KEY_FEED_UPDATE_THRESHOLD = "feed_update_threshold_key"
    const val KEY_FEED_USE_DEDICATED_FETCH_METHOD = "feed_use_dedicated_fetch_method"
    const val KEY_FEED_FETCH_CHANNEL_TABS = "feed_fetch_channel_tabs"

    const val SEARCH_SUGGESTIONS_LOCAL = "show_local_search_suggestions"
    const val SEARCH_SUGGESTIONS_REMOTE = "show_remote_search_suggestions"

    const val IMAGE_QUALITY_NONE = "image_quality_none"
    const val IMAGE_QUALITY_LOW = "image_quality_low"
    const val IMAGE_QUALITY_MEDIUM = "image_quality_medium"
    const val IMAGE_QUALITY_HIGH = "image_quality_high"

    val FEED_UPDATE_THRESHOLDS = listOf("0", "300", "900", "3600", "21600", "43200", "86400")

    const val DEFAULT_LOCALIZATION = "system"
    const val DEFAULT_SHOW_AGE_RESTRICTED_CONTENT = false
    const val DEFAULT_YOUTUBE_RESTRICTED_MODE = false
    val DEFAULT_SEARCH_SUGGESTIONS = setOf(SEARCH_SUGGESTIONS_LOCAL, SEARCH_SUGGESTIONS_REMOTE)
    const val DEFAULT_IMAGE_QUALITY = IMAGE_QUALITY_MEDIUM
    const val DEFAULT_SHOW_COMMENTS = true
    const val DEFAULT_SHOW_NEXT_VIDEO = true
    const val DEFAULT_SHOW_DESCRIPTION = true
    const val DEFAULT_SHOW_META_INFO = true
    const val DEFAULT_FEED_UPDATE_THRESHOLD = "300"
    const val DEFAULT_FEED_USE_DEDICATED_FETCH_METHOD = false
}
