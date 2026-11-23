package org.schabi.newpipe.extractor.services.youtube.linkHandler

import java.net.MalformedURLException
import java.util.Locale
import org.schabi.newpipe.extractor.utils.Utils

/** Keeps a post discussion distinct from video IDs and backend continuation URLs. */
class YoutubePostCommentsTarget private constructor(val postId: String) {
    val canonicalUrl: String get() = "https://www.youtube.com/post/$postId"
    val handlerId: String get() = "post:$postId"

    companion object {
        private val POST_ID = Regex("Ug[A-Za-z0-9_-]{1,126}")
        private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

        @JvmStatic
        fun fromPostId(id: String?): YoutubePostCommentsTarget? = id?.takeIf(POST_ID::matches)?.let(::YoutubePostCommentsTarget)

        @JvmStatic
        fun fromId(id: String?): YoutubePostCommentsTarget? = id?.takeIf { it.startsWith("post:") }?.removePrefix("post:")?.let(::fromPostId)

        @JvmStatic
        fun fromUrl(value: String?): YoutubePostCommentsTarget? {
            if (value.isNullOrBlank()) return null
            val url = try {
                Utils.stringToURL(value)
            } catch (_: MalformedURLException) {
                return null
            }
            if (!Utils.isHTTP(url) || url.userInfo != null || url.host.lowercase(Locale.ROOT) !in HOSTS) return null
            val path = url.path.removeSuffix("/")
            if (!path.startsWith("/post/")) return null
            return fromPostId(path.removePrefix("/post/"))
        }
    }
}
