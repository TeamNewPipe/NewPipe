/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItemExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor
import org.schabi.newpipe.extractor.post.PostInfoItem
import org.schabi.newpipe.extractor.post.PostInfoItemExtractor
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor

/**
 * API 24 plugin DEX may inherit interface defaults which this API 23 host has desugared. Adapt
 * foreign item extractors at collection boundaries so absent defaults execute the host's body.
 * Concrete plugin implementations and their errors are preserved; built-in objects are untouched.
 */
object PluginExtractorAdapters {
    @JvmStatic
    fun stream(extractor: StreamInfoItemExtractor): StreamInfoItemExtractor = if (isForeign(extractor)) StreamAdapter(extractor) else extractor

    @JvmStatic
    fun channel(extractor: ChannelInfoItemExtractor): ChannelInfoItemExtractor = if (isForeign(extractor)) ChannelAdapter(extractor) else extractor

    @JvmStatic
    fun playlist(extractor: PlaylistInfoItemExtractor): PlaylistInfoItemExtractor = if (isForeign(extractor)) PlaylistAdapter(extractor) else extractor

    @JvmStatic
    fun comments(extractor: CommentsInfoItemExtractor): CommentsInfoItemExtractor = if (isForeign(extractor)) CommentsAdapter(extractor) else extractor

    @JvmStatic
    fun bulletComments(extractor: BulletCommentsInfoItemExtractor): BulletCommentsInfoItemExtractor = if (isForeign(extractor)) BulletCommentsAdapter(extractor) else extractor

    @JvmStatic
    fun post(extractor: PostInfoItemExtractor): PostInfoItemExtractor = if (isForeign(extractor)) PostAdapter(extractor) else extractor

    private fun isForeign(extractor: InfoItemExtractor): Boolean = extractor.javaClass.classLoader !== InfoItemExtractor::class.java.classLoader

    private fun <T> readDefault(extractor: InfoItemExtractor, methodName: String, read: () -> T, fallback: () -> T): T = try {
        read()
    } catch (failure: AbstractMethodError) {
        val declaration = try {
            extractor.javaClass.getMethod(methodName)
        } catch (_: NoSuchMethodException) {
            null
        }
        // An error thrown inside an implemented method is a plugin bug, not an absent default.
        if (declaration != null && !declaration.declaringClass.isInterface) throw failure
        fallback()
    }

    private class StreamAdapter(private val delegate: StreamInfoItemExtractor) : StreamInfoItemExtractor by delegate {
        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<StreamInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<StreamInfoItemExtractor>.getThumbnails() })

        override fun getAlbumUrl(): String? = readDefault(delegate, "getAlbumUrl", { delegate.getAlbumUrl() }, { super<StreamInfoItemExtractor>.getAlbumUrl() })

        override fun isAd(): Boolean = readDefault(delegate, "isAd", { delegate.isAd() }, { super<StreamInfoItemExtractor>.isAd() })

        override fun getUploaderUrl(): String? = readDefault(delegate, "getUploaderUrl", { delegate.getUploaderUrl() }, { super<StreamInfoItemExtractor>.getUploaderUrl() })

        override fun getUploaderId(): String? = readDefault(delegate, "getUploaderId", { delegate.getUploaderId() }, { super<StreamInfoItemExtractor>.getUploaderId() })

        override fun getUploaderAvatarUrl(): String? = readDefault(delegate, "getUploaderAvatarUrl", { delegate.getUploaderAvatarUrl() }, { super<StreamInfoItemExtractor>.getUploaderAvatarUrl() })

        override fun isUploaderVerified(): Boolean = readDefault(delegate, "isUploaderVerified", { delegate.isUploaderVerified() }, { super<StreamInfoItemExtractor>.isUploaderVerified() })

        override fun getShortDescription(): String? = readDefault(delegate, "getShortDescription", { delegate.getShortDescription() }, { super<StreamInfoItemExtractor>.getShortDescription() })

        override fun isRoundPlayStream(): Boolean = readDefault(delegate, "isRoundPlayStream", { delegate.isRoundPlayStream() }, { super<StreamInfoItemExtractor>.isRoundPlayStream() })

        override fun isShortFormContent(): Boolean = readDefault(delegate, "isShortFormContent", { delegate.isShortFormContent() }, { super<StreamInfoItemExtractor>.isShortFormContent() })

        override fun requiresMembership(): Boolean = readDefault(delegate, "requiresMembership", { delegate.requiresMembership() }, { super<StreamInfoItemExtractor>.requiresMembership() })

        override fun getContentAvailability(): ContentAvailability = readDefault(delegate, "getContentAvailability", { delegate.getContentAvailability() }, { super<StreamInfoItemExtractor>.getContentAvailability() })
    }

    private class ChannelAdapter(private val delegate: ChannelInfoItemExtractor) : ChannelInfoItemExtractor by delegate {
        override fun getUploaderId(): String? = readDefault(delegate, "getUploaderId", { delegate.getUploaderId() }, { super<ChannelInfoItemExtractor>.getUploaderId() })

        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<ChannelInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<ChannelInfoItemExtractor>.getThumbnails() })
    }

    private class PlaylistAdapter(private val delegate: PlaylistInfoItemExtractor) : PlaylistInfoItemExtractor by delegate {
        override fun getUploaderId(): String? = readDefault(delegate, "getUploaderId", { delegate.getUploaderId() }, { super<PlaylistInfoItemExtractor>.getUploaderId() })

        override fun getUploaderUrl(): String? = readDefault(delegate, "getUploaderUrl", { delegate.getUploaderUrl() }, { super<PlaylistInfoItemExtractor>.getUploaderUrl() })

        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<PlaylistInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<PlaylistInfoItemExtractor>.getThumbnails() })

        override fun getPlaylistType(): PlaylistInfo.PlaylistType = readDefault(delegate, "getPlaylistType", { delegate.getPlaylistType() }, { super<PlaylistInfoItemExtractor>.getPlaylistType() })
    }

    private class CommentsAdapter(private val delegate: CommentsInfoItemExtractor) : CommentsInfoItemExtractor by delegate {
        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<CommentsInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<CommentsInfoItemExtractor>.getThumbnails() })

        override fun getLikeCount(): Int = readDefault(delegate, "getLikeCount", { delegate.getLikeCount() }, { super<CommentsInfoItemExtractor>.getLikeCount() })

        override fun getTextualLikeCount(): String? = readDefault(delegate, "getTextualLikeCount", { delegate.getTextualLikeCount() }, { super<CommentsInfoItemExtractor>.getTextualLikeCount() })

        override fun getCommentText(): Description? = readDefault(delegate, "getCommentText", { delegate.getCommentText() }, { super<CommentsInfoItemExtractor>.getCommentText() })

        override fun getCommentTextContent(): String? = readDefault(delegate, "getCommentTextContent", { delegate.getCommentTextContent() }, { super<CommentsInfoItemExtractor>.getCommentTextContent() })

        override fun getTextualUploadDate(): String? = readDefault(delegate, "getTextualUploadDate", { delegate.getTextualUploadDate() }, { super<CommentsInfoItemExtractor>.getTextualUploadDate() })

        override fun getUploadDate(): DateWrapper? = readDefault(delegate, "getUploadDate", { delegate.getUploadDate() }, { super<CommentsInfoItemExtractor>.getUploadDate() })

        override fun getCommentId(): String? = readDefault(delegate, "getCommentId", { delegate.getCommentId() }, { super<CommentsInfoItemExtractor>.getCommentId() })

        override fun getUploaderUrl(): String? = readDefault(delegate, "getUploaderUrl", { delegate.getUploaderUrl() }, { super<CommentsInfoItemExtractor>.getUploaderUrl() })

        override fun getUploaderName(): String? = readDefault(delegate, "getUploaderName", { delegate.getUploaderName() }, { super<CommentsInfoItemExtractor>.getUploaderName() })

        override fun getUploaderId(): String? = readDefault(delegate, "getUploaderId", { delegate.getUploaderId() }, { super<CommentsInfoItemExtractor>.getUploaderId() })

        override fun getUploaderAvatarUrl(): String? = readDefault(delegate, "getUploaderAvatarUrl", { delegate.getUploaderAvatarUrl() }, { super<CommentsInfoItemExtractor>.getUploaderAvatarUrl() })

        override fun getUploaderAvatars(): List<Image> = readDefault(delegate, "getUploaderAvatars", { delegate.getUploaderAvatars() }, { super<CommentsInfoItemExtractor>.getUploaderAvatars() })

        override fun isHeartedByUploader(): Boolean = readDefault(delegate, "isHeartedByUploader", { delegate.isHeartedByUploader() }, { super<CommentsInfoItemExtractor>.isHeartedByUploader() })

        override fun isPinned(): Boolean = readDefault(delegate, "isPinned", { delegate.isPinned() }, { super<CommentsInfoItemExtractor>.isPinned() })

        override fun isUploaderVerified(): Boolean = readDefault(delegate, "isUploaderVerified", { delegate.isUploaderVerified() }, { super<CommentsInfoItemExtractor>.isUploaderVerified() })

        override fun getStreamPosition(): Int = readDefault(delegate, "getStreamPosition", { delegate.getStreamPosition() }, { super<CommentsInfoItemExtractor>.getStreamPosition() })

        override fun getReplyCount(): Int = readDefault(delegate, "getReplyCount", { delegate.getReplyCount() }, { super<CommentsInfoItemExtractor>.getReplyCount() })

        override fun getReplies(): Page? = readDefault(delegate, "getReplies", { delegate.getReplies() }, { super<CommentsInfoItemExtractor>.getReplies() })

        override fun getPictures(): Collection<Image> = readDefault(delegate, "getPictures", { delegate.getPictures() }, { super<CommentsInfoItemExtractor>.getPictures() })
    }

    private class BulletCommentsAdapter(private val delegate: BulletCommentsInfoItemExtractor) : BulletCommentsInfoItemExtractor by delegate {
        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<BulletCommentsInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<BulletCommentsInfoItemExtractor>.getThumbnails() })

        override fun getName(): String? = readDefault(delegate, "getName", { delegate.getName() }, { super<BulletCommentsInfoItemExtractor>.getName() })

        override fun getUrl(): String? = readDefault(delegate, "getUrl", { delegate.getUrl() }, { super<BulletCommentsInfoItemExtractor>.getUrl() })

        override fun getCommentText(): String? = readDefault(delegate, "getCommentText", { delegate.getCommentText() }, { super<BulletCommentsInfoItemExtractor>.getCommentText() })

        override fun getAuthorName(): String? = readDefault(delegate, "getAuthorName", { delegate.getAuthorName() }, { super<BulletCommentsInfoItemExtractor>.getAuthorName() })

        override fun getAuthorAvatarUrl(): String? = readDefault(delegate, "getAuthorAvatarUrl", { delegate.getAuthorAvatarUrl() }, { super<BulletCommentsInfoItemExtractor>.getAuthorAvatarUrl() })

        override fun getAmountText(): String? = readDefault(delegate, "getAmountText", { delegate.getAmountText() }, { super<BulletCommentsInfoItemExtractor>.getAmountText() })

        override fun getArgbColor(): Int = readDefault(delegate, "getArgbColor", { delegate.getArgbColor() }, { super<BulletCommentsInfoItemExtractor>.getArgbColor() })

        override fun getPosition(): BulletCommentsInfoItem.Position = readDefault(delegate, "getPosition", { delegate.getPosition() }, { super<BulletCommentsInfoItemExtractor>.getPosition() })

        override fun getRelativeFontSize(): Double = readDefault(delegate, "getRelativeFontSize", { delegate.getRelativeFontSize() }, { super<BulletCommentsInfoItemExtractor>.getRelativeFontSize() })

        override fun getLastingTime(): Int = readDefault(delegate, "getLastingTime", { delegate.getLastingTime() }, { super<BulletCommentsInfoItemExtractor>.getLastingTime() })

        override fun getPinnedTimeMillis(): Long = readDefault(delegate, "getPinnedTimeMillis", { delegate.getPinnedTimeMillis() }, { super<BulletCommentsInfoItemExtractor>.getPinnedTimeMillis() })

        override fun isLive(): Boolean = readDefault(delegate, "isLive", { delegate.isLive() }, { super<BulletCommentsInfoItemExtractor>.isLive() })
    }

    private class PostAdapter(private val delegate: PostInfoItemExtractor) : PostInfoItemExtractor by delegate {
        override fun getThumbnailUrl(): String? = readDefault(delegate, "getThumbnailUrl", { delegate.getThumbnailUrl() }, { super<PostInfoItemExtractor>.getThumbnailUrl() })

        override fun getThumbnails(): List<Image> = readDefault(delegate, "getThumbnails", { delegate.getThumbnails() }, { super<PostInfoItemExtractor>.getThumbnails() })

        override fun getPostId(): String? = readDefault(delegate, "getPostId", { delegate.getPostId() }, { super<PostInfoItemExtractor>.getPostId() })

        override fun getContent(): String? = readDefault(delegate, "getContent", { delegate.getContent() }, { super<PostInfoItemExtractor>.getContent() })

        override fun getUploaderName(): String? = readDefault(delegate, "getUploaderName", { delegate.getUploaderName() }, { super<PostInfoItemExtractor>.getUploaderName() })

        override fun getUploaderUrl(): String? = readDefault(delegate, "getUploaderUrl", { delegate.getUploaderUrl() }, { super<PostInfoItemExtractor>.getUploaderUrl() })

        override fun getUploaderAvatars(): List<Image> = readDefault(delegate, "getUploaderAvatars", { delegate.getUploaderAvatars() }, { super<PostInfoItemExtractor>.getUploaderAvatars() })

        override fun getTextualUploadDate(): String? = readDefault(delegate, "getTextualUploadDate", { delegate.getTextualUploadDate() }, { super<PostInfoItemExtractor>.getTextualUploadDate() })

        override fun getUploadDate(): DateWrapper? = readDefault(delegate, "getUploadDate", { delegate.getUploadDate() }, { super<PostInfoItemExtractor>.getUploadDate() })

        override fun getLikeCount(): Long = readDefault(delegate, "getLikeCount", { delegate.getLikeCount() }, { super<PostInfoItemExtractor>.getLikeCount() })

        override fun getCommentCount(): Long = readDefault(delegate, "getCommentCount", { delegate.getCommentCount() }, { super<PostInfoItemExtractor>.getCommentCount() })

        override fun getTextualLikeCount(): String? = readDefault(delegate, "getTextualLikeCount", { delegate.getTextualLikeCount() }, { super<PostInfoItemExtractor>.getTextualLikeCount() })

        override fun getTextualCommentCount(): String? = readDefault(delegate, "getTextualCommentCount", { delegate.getTextualCommentCount() }, { super<PostInfoItemExtractor>.getTextualCommentCount() })

        override fun getImages(): List<Image> = readDefault(delegate, "getImages", { delegate.getImages() }, { super<PostInfoItemExtractor>.getImages() })

        override fun getAttachment(): PostInfoItem.Attachment? = readDefault(delegate, "getAttachment", { delegate.getAttachment() }, { super<PostInfoItemExtractor>.getAttachment() })

        override fun getPoll(): PostInfoItem.Poll? = readDefault(delegate, "getPoll", { delegate.getPoll() }, { super<PostInfoItemExtractor>.getPoll() })

        override fun isPinned(): Boolean = readDefault(delegate, "isPinned", { delegate.isPinned() }, { super<PostInfoItemExtractor>.isPinned() })

        override fun isEdited(): Boolean = readDefault(delegate, "isEdited", { delegate.isEdited() }, { super<PostInfoItemExtractor>.isEdited() })
    }
}
