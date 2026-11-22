/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class VimeoChannelTabLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String = VimeoChannelLinkHandlerFactory.INSTANCE.getId(url)

    override fun getUrl(id: String, contentFilter: List<FilterItem>, sortFilter: List<FilterItem>?): String {
        if (contentFilter.size > 1 || contentFilter.any { it.name != ChannelTabs.VIDEOS } || !sortFilter.isNullOrEmpty()) {
            throw ContentNotSupportedException("Vimeo only provides the public Videos tab")
        }
        return VimeoChannelLinkHandlerFactory.INSTANCE.getUrl(id)
    }

    override fun onAcceptUrl(url: String): Boolean = VimeoChannelTarget.fromUrl(url) != null

    override fun fromUrl(url: String, baseUrl: String): ListLinkHandler = videos(super.fromUrl(url, baseUrl))

    override fun fromId(id: String): ListLinkHandler = videos(super.fromId(id))

    override fun fromId(id: String, baseUrl: String): ListLinkHandler = videos(super.fromId(id, baseUrl))

    private fun videos(handler: ListLinkHandler): ListLinkHandler = ListLinkHandler(handler.originalUrl, handler.url, handler.id, listOf(VIDEOS), emptyList())

    companion object {
        @JvmField val INSTANCE = VimeoChannelTabLinkHandlerFactory()

        @JvmField val VIDEOS = FilterItem(0, ChannelTabs.VIDEOS)
    }
}
