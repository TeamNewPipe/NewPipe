/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.extractor.services.vimeo.VimeoVideoTarget
import org.schabi.newpipe.fragments.detail.TabAdapter

/** Live event chat is separate from the official API's public video comments. */
object VimeoCommentsSupport {
    @JvmStatic
    fun supportsUrl(serviceId: Int, url: String?): Boolean = serviceId != ServiceList.Vimeo.serviceId ||
        VimeoPublicApi.isConfigured() && VimeoVideoTarget.fromUrl(url) != null

    /** Token storage is separate from preferences, so recheck an existing detail screen on resume. */
    @JvmStatic
    fun shouldRefreshTabs(serviceId: Int, adapter: TabAdapter?, commentsTabTag: String, shouldShowComments: Boolean): Boolean = serviceId == ServiceList.Vimeo.serviceId && adapter != null &&
        (adapter.getItemPositionByTitle(commentsTabTag) >= 0) != shouldShowComments
}
