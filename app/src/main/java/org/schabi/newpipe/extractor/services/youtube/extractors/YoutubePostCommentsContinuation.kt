package org.schabi.newpipe.extractor.services.youtube.extractors

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import com.grack.nanojson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import org.schabi.newpipe.extractor.comments.CommentSortOrder
import org.schabi.newpipe.extractor.exceptions.ParsingException

/**
 * Public post context and browse continuation, without a video/player request or account tokens.
 * Wire fields follow Invidious's decode_ucid_from_post_protobuf/fetch_community_post_comments:
 * https://github.com/iv-org/invidious/blob/9a5c91cf808966fb6a4516c9e21ec7a7ad648f16/src/invidious/channels/community.cr
 * https://github.com/iv-org/invidious/blob/9a5c91cf808966fb6a4516c9e21ec7a7ad648f16/src/invidious/comments/youtube.cr
 */
internal object YoutubePostCommentsContinuation {
    private const val MAX_PARAMS_LENGTH = 32_768
    private val channelIdPattern = Regex("UC[A-Za-z0-9_-]{22}")

    fun channelId(response: JsonObject, postId: String): String {
        val endpoint = response.getObject("endpoint").getObject("browseEndpoint")
        if (endpoint.getString("browseId") != "FEpost_detail") {
            throw ParsingException("YouTube did not resolve a community post")
        }
        val params = endpoint.getString("params").orEmpty()
        if (params.isBlank() || params.length > MAX_PARAMS_LENGTH) {
            throw ParsingException("YouTube post context is missing or too large")
        }
        try {
            val decoded = Base64.getUrlDecoder().decode(URLDecoder.decode(params, UTF_8.name()))
            val outer = CodedInputStream.newInstance(decoded)
            while (!outer.isAtEnd) {
                val tag = outer.readTag()
                if (tag == 0) break
                if (WireFormat.getTagFieldNumber(tag) == 56 &&
                    WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED
                ) {
                    return readPostContext(outer.readByteArray(), postId)
                }
                if (!outer.skipField(tag)) break
            }
        } catch (error: IOException) {
            throw ParsingException("Could not decode YouTube post context", error)
        } catch (error: IllegalArgumentException) {
            throw ParsingException("Could not decode YouTube post context", error)
        }
        throw ParsingException("YouTube post context does not contain a channel")
    }

    private fun readPostContext(bytes: ByteArray, expectedPostId: String): String {
        val input = CodedInputStream.newInstance(bytes)
        var channelId: String? = null
        var resolvedPostId: String? = null
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            val field = WireFormat.getTagFieldNumber(tag)
            if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED &&
                (field == 2 || field == 3)
            ) {
                val value = input.readStringRequireUtf8()
                if (field == 2) channelId = value else resolvedPostId = value
            } else if (!input.skipField(tag)) {
                break
            }
        }
        if (resolvedPostId != null && resolvedPostId != expectedPostId) {
            throw ParsingException("YouTube resolved a different community post")
        }
        return channelId?.takeIf(channelIdPattern::matches)
            ?: throw ParsingException("YouTube post context has no valid channel ID")
    }

    fun initial(postId: String, channelId: String, order: CommentSortOrder): String {
        require(postId.isNotBlank())
        require(channelIdPattern.matches(channelId))
        val parameters = message {
            writeString(2, "posts")
            writeByteArray(
                53,
                message {
                    writeByteArray(
                        4,
                        message {
                            writeInt64(6, if (order == CommentSortOrder.NEWEST) 1 else 0)
                            writeInt64(15, 2)
                            writeInt64(25, 0)
                            writeString(29, postId)
                            writeString(30, channelId)
                        }
                    )
                    writeInt64(7, 0)
                    writeString(8, "comments-section")
                }
            )
        }
        val continuation = message {
            writeByteArray(
                80226972,
                message {
                    writeString(2, "FEcomment_post_detail_page_web_top_level")
                    writeString(3, Base64.getUrlEncoder().encodeToString(parameters))
                }
            )
        }
        return URLEncoder.encode(Base64.getUrlEncoder().encodeToString(continuation), UTF_8.name())
    }

    private fun message(write: CodedOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = CodedOutputStream.newInstance(bytes)
        output.write()
        output.flush()
        return bytes.toByteArray()
    }
}
