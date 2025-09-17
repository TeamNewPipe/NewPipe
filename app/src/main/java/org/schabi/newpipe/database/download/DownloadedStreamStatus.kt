package org.schabi.newpipe.database.download

enum class DownloadedStreamStatus(val value: Int) {
    IN_PROGRESS(0),
    AVAILABLE(1),
    MISSING(2),
    UNLINKED(3);

    companion object {
        fun fromValue(value: Int): DownloadedStreamStatus = entries.firstOrNull {
            it.value == value
        } ?: IN_PROGRESS
    }
}
