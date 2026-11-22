/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class VimeoChannelLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String = VimeoChannelTarget.fromUrl(url)?.id
        ?: throw ParsingException("Not a supported Vimeo creator or channel URL")

    override fun getUrl(id: String, contentFilter: List<FilterItem>, sortFilter: List<FilterItem>?): String = VimeoChannelTarget.fromId(id)?.canonicalUrl ?: throw ParsingException("Invalid Vimeo channel identity")

    override fun onAcceptUrl(url: String): Boolean = VimeoChannelTarget.fromUrl(url) != null

    companion object {
        @JvmField val INSTANCE = VimeoChannelLinkHandlerFactory()
    }
}
