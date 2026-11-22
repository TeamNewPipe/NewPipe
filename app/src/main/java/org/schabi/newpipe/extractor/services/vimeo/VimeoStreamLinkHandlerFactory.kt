/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory

class VimeoStreamLinkHandlerFactory private constructor() : LinkHandlerFactory() {
    override fun getId(url: String): String = VimeoVideoTarget.fromUrl(url)?.id
        ?: throw ParsingException("Not a supported Vimeo video URL")

    override fun getUrl(id: String): String = VimeoVideoTarget.fromId(id)?.canonicalUrl
        ?: throw ParsingException("Invalid Vimeo video identity")

    override fun onAcceptUrl(url: String): Boolean = VimeoVideoTarget.fromUrl(url) != null

    companion object {
        @JvmField val INSTANCE = VimeoStreamLinkHandlerFactory()
    }
}
