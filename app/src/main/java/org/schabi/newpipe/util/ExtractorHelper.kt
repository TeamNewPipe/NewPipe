/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * ExtractorHelper.kt is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.text.TextLinkifier
import org.schabi.newpipe.util.text.TextLinkifier.SET_LINK_MOVEMENT_METHOD

object ExtractorHelper {
    private val TAG = ExtractorHelper::class.java.simpleName
    private val CACHE = InfoCache

    private fun checkServiceId(serviceId: Int) {
        if (serviceId == NO_SERVICE_ID) {
            throw IllegalArgumentException("serviceId is NO_SERVICE_ID")
        }
    }

    suspend fun searchFor(
        serviceId: Int,
        searchString: String,
        contentFilter: List<String>,
        sortFilter: String?,
        forceLoad: Boolean = false
    ): SearchInfo {
        checkServiceId(serviceId)
        val cacheKey = "$searchString:$contentFilter:$sortFilter"
        return checkCache(forceLoad, serviceId, cacheKey, InfoCache.Type.SEARCH) {
            val service = NewPipe.getService(serviceId)
            SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(searchString, contentFilter, sortFilter))
        }
    }

    suspend fun getMoreSearchItems(
        serviceId: Int,
        searchString: String,
        contentFilter: List<String>,
        sortFilter: String?,
        page: Page
    ): InfoItemsPage<InfoItem> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        val service = NewPipe.getService(serviceId)
        SearchInfo.getMoreItems(service, service.searchQHFactory.fromQuery(searchString, contentFilter, sortFilter), page)
    }

    suspend fun suggestionsFor(serviceId: Int, query: String): List<String> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        val extractor = NewPipe.getService(serviceId).suggestionExtractor
        extractor?.suggestionList(query) ?: emptyList()
    }

    suspend fun getStreamInfo(
        serviceId: Int,
        url: String,
        forceLoad: Boolean
    ): StreamInfo {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.STREAM) {
            StreamInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    }

    suspend fun getChannelInfo(
        serviceId: Int,
        url: String,
        forceLoad: Boolean
    ): ChannelInfo {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.CHANNEL) {
            ChannelInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    }

    suspend fun getChannelTab(
        serviceId: Int,
        listLinkHandler: ListLinkHandler,
        forceLoad: Boolean
    ): ChannelTabInfo {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, listLinkHandler.url, InfoCache.Type.CHANNEL_TAB) {
            ChannelTabInfo.getInfo(NewPipe.getService(serviceId), listLinkHandler)
        }
    }

    suspend fun getMoreChannelTabItems(
        serviceId: Int,
        listLinkHandler: ListLinkHandler,
        nextPage: Page
    ): InfoItemsPage<InfoItem> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId), listLinkHandler, nextPage)
    }

    suspend fun getCommentsInfo(
        serviceId: Int,
        url: String,
        forceLoad: Boolean
    ): CommentsInfo {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.COMMENTS) {
            CommentsInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    }

    suspend fun getMoreCommentItems(
        serviceId: Int,
        info: CommentsInfo,
        nextPage: Page
    ): InfoItemsPage<CommentsInfoItem> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage)
    }

    suspend fun getMoreCommentItems(
        serviceId: Int,
        url: String,
        nextPage: Page
    ): InfoItemsPage<CommentsInfoItem> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        CommentsInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage)
    }

    suspend fun getPlaylistInfo(
        serviceId: Int,
        url: String,
        forceLoad: Boolean
    ): PlaylistInfo {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.PLAYLIST) {
            PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    }

    suspend fun getMorePlaylistItems(
        serviceId: Int,
        url: String,
        nextPage: Page
    ): InfoItemsPage<StreamInfoItem> = withContext(Dispatchers.IO) {
        checkServiceId(serviceId)
        PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage)
    }

    suspend fun getKioskInfo(
        serviceId: Int,
        url: String,
        forceLoad: Boolean
    ): KioskInfo {
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.KIOSK) {
            KioskInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    }

    suspend fun getMoreKioskItems(
        serviceId: Int,
        url: String,
        nextPage: Page
    ): InfoItemsPage<StreamInfoItem> = withContext(Dispatchers.IO) {
        KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage)
    }

    //  Cache

    private suspend fun <I : Info> checkCache(
        forceLoad: Boolean,
        serviceId: Int,
        url: String,
        cacheType: InfoCache.Type,
        loadFromNetwork: suspend () -> I
    ): I {
        checkServiceId(serviceId)

        if (!forceLoad) {
            val cachedInfo = loadFromCache<I>(serviceId, url, cacheType)
            if (cachedInfo != null) {
                return cachedInfo
            }
        } else {
            CACHE.removeInfo(serviceId, url, cacheType)
        }

        return withContext(Dispatchers.IO) {
            val info = loadFromNetwork()
            CACHE.putInfo(serviceId, url, info, cacheType)
            info
        }
    }

    private fun <I : Info> loadFromCache(
        serviceId: Int,
        url: String,
        cacheType: InfoCache.Type
    ): I? {
        checkServiceId(serviceId)
        @Suppress("UNCHECKED_CAST")
        val info = CACHE.getFromKey(serviceId, url, cacheType) as? I
        if (DEBUG) {
            Log.d(TAG, "loadFromCache() called, info > $info")
        }
        return info
    }

    fun isCached(
        serviceId: Int,
        url: String,
        cacheType: InfoCache.Type
    ): Boolean {
        return loadFromCache<Info>(serviceId, url, cacheType) != null
    }

    //  Utils

    fun showMetaInfoInTextView(
        metaInfos: List<MetaInfo>?,
        metaInfoTextView: TextView,
        metaInfoSeparator: View
    ) {
        val context = metaInfoTextView.context
        if (metaInfos.isNullOrEmpty() ||
            !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.show_meta_info_key), true
            )
        ) {
            metaInfoTextView.visibility = View.GONE
            metaInfoSeparator.visibility = View.GONE
        } else {
            val stringBuilder = StringBuilder()
            for (metaInfo in metaInfos) {
                if (!metaInfo.title.isNullOrEmpty()) {
                    stringBuilder.append("<b>").append(metaInfo.title).append("</b>")
                        .append(Localization.DOT_SEPARATOR)
                }

                var content = metaInfo.content.content().trim()
                if (content.endsWith(".")) {
                    content = content.substring(0, content.length - 1)
                }
                stringBuilder.append(content)

                for (i in metaInfo.urls.indices) {
                    if (i == 0) {
                        stringBuilder.append(Localization.DOT_SEPARATOR)
                    } else {
                        stringBuilder.append("<br/><br/>")
                    }

                    stringBuilder
                        .append("<a href=\"").append(metaInfo.urls[i]).append("\">")
                        .append(capitalizeIfAllUppercase(metaInfo.urlTexts[i].trim()))
                        .append("</a>")
                }
            }

            metaInfoSeparator.visibility = View.VISIBLE
            TextLinkifier.fromHtml(
                metaInfoTextView, stringBuilder.toString(),
                HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING, null, null,
                SET_LINK_MOVEMENT_METHOD
            )
        }
    }

    private fun capitalizeIfAllUppercase(text: String): String {
        for (i in text.indices) {
            if (Character.isLowerCase(text[i])) {
                return text
            }
        }

        return if (text.isEmpty()) {
            text
        } else {
            text.substring(0, 1).uppercase() + text.substring(1).lowercase()
        }
    }
}
