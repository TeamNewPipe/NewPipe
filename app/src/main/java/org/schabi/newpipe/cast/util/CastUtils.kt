package org.schabi.newpipe.cast.util

import java.util.regex.Pattern

object CastUtils {

    private val VIDEO_ID_PATTERNS = listOf(
        Pattern.compile("(?:v=|/v/|/embed/|/shorts/|youtu\\.be/|/e/)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("[&?]v=([a-zA-Z0-9_-]{11})")
    )

    private val RAW_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$")

    /**
     * Extracts an 11-character YouTube video ID from various URL formats
     * (standard watch, shorts, embeds, youtu.be, mobile) or returns the ID
     * if an 11-character ID is passed directly.
     */
    @JvmStatic
    fun extractYouTubeVideoId(urlOrId: String?): String? {
        if (urlOrId.isNullOrBlank()) {
            return null
        }
        val clean = urlOrId.trim()
        if (clean.length == 11 && RAW_ID_PATTERN.matcher(clean).matches()) {
            return clean
        }
        for (pattern in VIDEO_ID_PATTERNS) {
            val matcher = pattern.matcher(clean)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
