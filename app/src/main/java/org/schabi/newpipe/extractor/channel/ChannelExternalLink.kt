package org.schabi.newpipe.extractor.channel

import java.io.Serializable

data class ChannelExternalLink(
    val title: String,
    val url: String
) : Serializable {
    val displayText: String
        get() = if (title.isBlank()) url else "$title: $url"
}
