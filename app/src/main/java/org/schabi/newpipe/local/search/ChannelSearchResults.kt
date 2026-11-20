/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.search

import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory
import org.schabi.newpipe.fragments.list.channel.ChannelTabFragment
import org.schabi.newpipe.profiles.KidsMode

/**
 * The toolbar filters fetched channel items while typing. Submitting a YouTube search uses
 * its existing channel-scoped extractor endpoint so older, unloaded videos can be found.
 * Other services keep their local filter until they expose a compatible channel search.
 */
object ChannelSearchResults {
    private val youtubeChannelId = Regex("UC[A-Za-z0-9_-]{22}")
    private const val MAX_QUERY_LENGTH = 256

    @JvmStatic
    fun handlerFor(serviceId: Int, channelId: String, query: String): ListLinkHandler? {
        val text = query.trim()
        if (serviceId != ServiceList.YouTube.serviceId ||
            !youtubeChannelId.matches(channelId) ||
            text.isEmpty() ||
            text.length > MAX_QUERY_LENGTH
        ) {
            return null
        }
        return runCatching {
            val factory = YoutubeChannelTabLinkHandlerFactory.getInstance()
            val url = YoutubeChannelTabLinkHandlerFactory.appendSearchQueryIfNeeded(
                "https://www.youtube.com/channel/$channelId/search",
                text
            )
            factory.fromUrl(url)
        }.getOrNull()
    }

    @JvmStatic
    fun open(fragment: Fragment, channel: ChannelInfo?, query: String): Boolean {
        if (!fragment.isAdded || channel == null ||
            !KidsMode.isSearchAllowed(fragment.requireContext(), channel.serviceId)
        ) {
            return false
        }
        val handler = handlerFor(channel.serviceId, channel.id, query) ?: return false
        val resultTitle = "${channel.name} · ${query.trim()}"
        val results = ChannelTabFragment.getInstance(channel.serviceId, handler, resultTitle, null)
        fragment.parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, results)
            .addToBackStack(null)
            .commit()
        return true
    }
}
