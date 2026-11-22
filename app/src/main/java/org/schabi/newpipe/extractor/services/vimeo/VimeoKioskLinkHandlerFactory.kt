/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class VimeoKioskLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String {
        if (VimeoChannelTarget.fromUrl(url)?.id != "channel:staffpicks") throw ParsingException("Unknown Vimeo kiosk")
        return STAFF_PICKS
    }

    override fun getUrl(id: String, contentFilter: List<FilterItem>, sortFilter: List<FilterItem>?): String {
        if (id != STAFF_PICKS || contentFilter.isNotEmpty() || !sortFilter.isNullOrEmpty()) throw ParsingException("Unknown Vimeo kiosk")
        return "https://vimeo.com/channels/staffpicks"
    }

    override fun onAcceptUrl(url: String): Boolean = VimeoChannelTarget.fromUrl(url)?.id == "channel:staffpicks"

    companion object {
        const val STAFF_PICKS = "Staff Picks"

        @JvmField val INSTANCE = VimeoKioskLinkHandlerFactory()
    }
}
