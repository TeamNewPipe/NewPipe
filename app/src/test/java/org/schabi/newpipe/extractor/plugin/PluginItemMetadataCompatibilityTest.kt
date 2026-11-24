/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemExtractor
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItemsCollector
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItemsCollector
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemsCollector
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType

class PluginItemMetadataCompatibilityTest {
    @Test
    fun oldSavedItemsKeepExistingFieldsAndDefaultNewMetadata() {
        val input = requireNotNull(javaClass.getResourceAsStream("/plugin-fixtures/legacy-items-v1.51.ser"))
        val items = ObjectInputStream(input).use { it.readObject() as List<*> }
        val stream = items[0] as StreamInfoItem
        assertEquals("Legacy video", stream.name)
        assertEquals(123L, stream.duration)
        assertEquals("https://fixture.example/album", stream.albumUrl)
        assertNull(stream.uploaderId)
        val channel = items[1] as ChannelInfoItem
        assertEquals(456L, channel.subscriberCount)
        assertNull(channel.uploaderId)
        val playlist = items[2] as PlaylistInfoItem
        assertEquals(8L, playlist.streamCount)
        assertNull(playlist.uploaderId)
        assertNull(playlist.uploaderUrl)
        val bullet = items[3] as BulletCommentsInfoItem
        assertEquals("Live plain text", bullet.commentText)
        assertTrue(bullet.isLive)
        assertNull(bullet.authorName)
        assertNull(bullet.authorAvatarUrl)
        assertNull(bullet.amountText)
        assertEquals(0L, bullet.pinnedTimeMillis)
    }

    @Test
    fun foreignItemMetadataFlowsThroughCollectorsAndSerialization() {
        val streams = StreamInfoItemsCollector(12345)
        streams.commit(
            foreign(
                StreamInfoItemExtractor::class.java,
                object : StreamInfoItemExtractor {
                    override fun getName(): String = "Video"
                    override fun getUrl(): String = "https://fixture.example/video"
                    override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM
                    override fun getDuration(): Long = 30
                    override fun getViewCount(): Long = 100
                    override fun getUploaderName(): String = "Author"
                    override fun getUploaderId(): String = "stream-author"
                    override fun getTextualUploadDate(): String? = null
                    override fun getUploadDate(): DateWrapper? = null
                }
            )
        )
        val channels = ChannelInfoItemsCollector(12345)
        channels.commit(
            foreign(
                ChannelInfoItemExtractor::class.java,
                object : ChannelInfoItemExtractor {
                    override fun getName(): String = "Channel"
                    override fun getUrl(): String = "https://fixture.example/channel"
                    override fun getDescription(): String = "Channel description"
                    override fun getSubscriberCount(): Long = 10
                    override fun getStreamCount(): Long = 2
                    override fun isVerified(): Boolean = false
                    override fun getUploaderId(): String = "channel-author"
                }
            )
        )
        val playlists = PlaylistInfoItemsCollector(12345)
        playlists.commit(
            foreign(
                PlaylistInfoItemExtractor::class.java,
                object : PlaylistInfoItemExtractor {
                    override fun getName(): String = "Playlist"
                    override fun getUrl(): String = "https://fixture.example/playlist"
                    override fun getUploaderName(): String = "Author"
                    override fun getUploaderId(): String = "playlist-author"
                    override fun getUploaderUrl(): String = "https://fixture.example/author"
                    override fun getStreamCount(): Long = 2
                }
            )
        )
        val comments = CommentsInfoItemsCollector(12345)
        comments.commit(
            foreign(
                CommentsInfoItemExtractor::class.java,
                object : CommentsInfoItemExtractor {
                    override fun getName(): String = "Comment"
                    override fun getUrl(): String = "https://fixture.example/video"
                    override fun getUploaderId(): String = "comment-author"
                    override fun getCommentText(): Description = Description("<b>Comment</b>", Description.HTML)
                }
            )
        )
        val bullets = BulletCommentsInfoItemsCollector(12345)
        bullets.commit(
            foreign(
                BulletCommentsInfoItemExtractor::class.java,
                object : BulletCommentsInfoItemExtractor {
                    override fun getName(): String = "Live comment"
                    override fun getUrl(): String = "https://fixture.example/live"
                    override fun getDuration(): Duration = Duration.ofSeconds(2)
                    override fun getCommentText(): String = "A paid comment"
                    override fun getAuthorName(): String = "Live author"
                    override fun getAuthorAvatarUrl(): String = "https://fixture.example/avatar.png"
                    override fun getAmountText(): String = "5 EUR"
                    override fun getPinnedTimeMillis(): Long = 30000
                }
            )
        )
        listOf(streams, channels, playlists, comments, bullets).forEach { assertTrue(it.errors.isEmpty()) }
        val collected = arrayListOf(streams.items.single(), channels.items.single(), playlists.items.single(), comments.items.single(), bullets.items.single())
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(collected) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as List<*> }
        assertEquals("stream-author", (restored[0] as StreamInfoItem).uploaderId)
        assertEquals("channel-author", (restored[1] as ChannelInfoItem).uploaderId)
        assertEquals("playlist-author", (restored[2] as PlaylistInfoItem).uploaderId)
        assertEquals("https://fixture.example/author", (restored[2] as PlaylistInfoItem).uploaderUrl)
        assertEquals("comment-author", (restored[3] as CommentsInfoItem).uploaderId)
        assertEquals(Description.HTML, (restored[3] as CommentsInfoItem).commentText.type)
        val bullet = restored[4] as BulletCommentsInfoItem
        assertEquals("A paid comment", bullet.commentText)
        assertEquals("Live author", bullet.authorName)
        assertEquals("https://fixture.example/avatar.png", bullet.authorAvatarUrl)
        assertEquals("5 EUR", bullet.amountText)
        assertEquals(30000L, bullet.pinnedTimeMillis)
    }

    private fun <T> foreign(type: Class<T>, fixture: T): T {
        val loader = object : ClassLoader(type.classLoader) {}
        return type.cast(Proxy.newProxyInstance(loader, arrayOf(type)) { _, method, arguments -> method.invoke(fixture, *(arguments ?: emptyArray())) })
    }
}
