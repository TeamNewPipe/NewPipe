/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class VimeoCommentsLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String = VimeoVideoTarget.fromUrl(url)?.id
        ?: throw ParsingException("Not a supported Vimeo video URL")

    override fun getUrl(id: String, contentFilter: List<FilterItem>, sortFilter: List<FilterItem>?): String {
        if (contentFilter.isNotEmpty() || !sortFilter.isNullOrEmpty()) {
            throw ContentNotSupportedException("Vimeo comment filters are unavailable")
        }
        return VimeoVideoTarget.fromId(id)?.canonicalUrl ?: throw ParsingException("Invalid Vimeo video identity")
    }

    override fun onAcceptUrl(url: String): Boolean = VimeoVideoTarget.fromUrl(url) != null

    companion object {
        @JvmField val INSTANCE = VimeoCommentsLinkHandlerFactory()
    }
}
