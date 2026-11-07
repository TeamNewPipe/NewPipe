/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class BlockedChannelManagerTest {
    private val blockedUrl = "https://www.youtube.com/channel/blocked"
    private val otherUrl = "https://www.youtube.com/channel/other"

    private fun stream(name: String, uploaderUrl: String?) = StreamInfoItem(0, "https://www.youtube.com/watch?v=$name", name, StreamType.VIDEO_STREAM).apply {
        this.uploaderUrl = uploaderUrl
    }

    private fun channel(url: String) = ChannelInfoItem(0, url, "channel")

    @Test
    fun `nothing is removed when no channel is blocked`() {
        val items = listOf<InfoItem>(stream("a", blockedUrl), channel(blockedUrl))
        assertSame(items, BlockedChannelManager.filterBlocked(items, emptySet()))
    }

    @Test
    fun `streams of a blocked channel are removed`() {
        val kept = stream("kept", otherUrl)
        val items = listOf(stream("gone", blockedUrl), kept, stream("gone too", blockedUrl))
        assertEquals(listOf(kept), BlockedChannelManager.filterBlocked(items, setOf(blockedUrl)))
    }

    @Test
    fun `a blocked channel is removed from channel results`() {
        val kept = channel(otherUrl)
        val items = listOf<InfoItem>(channel(blockedUrl), kept)
        assertEquals(listOf<InfoItem>(kept), BlockedChannelManager.filterBlocked(items, setOf(blockedUrl)))
    }

    @Test
    fun `streams without an uploader url are kept`() {
        val noUploader = stream("none", null)
        val blankUploader = stream("blank", "  ")
        val items = listOf(noUploader, blankUploader)
        assertEquals(items, BlockedChannelManager.filterBlocked(items, setOf(blockedUrl)))
    }

    @Test
    fun `other kinds of items are always kept`() {
        val playlist = PlaylistInfoItem(0, blockedUrl, "playlist")
        assertEquals(InfoType.PLAYLIST, playlist.infoType)
        val items = listOf<InfoItem>(playlist)
        assertEquals(items, BlockedChannelManager.filterBlocked(items, setOf(blockedUrl)))
    }
}
