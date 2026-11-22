package org.schabi.newpipe.util

/** Only exact, provider-supplied stream categories are accepted, never inferred from a title. */
object HistoryContentCategory {
    @JvmStatic
    fun fromProvider(value: String?): String? = when (value?.trim()?.lowercase()) {
        "track", "tracks", "song" -> "TRACKS"
        "podcast", "podcasts", "podcast episode" -> "PODCASTS"
        "course", "courses", "course lesson" -> "COURSES"
        else -> null
    }
}
