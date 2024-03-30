/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * ExtractorHelper.java is part of NewPipe
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

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.MaybeSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Supplier
import org.schabi.newpipe.MainActivity
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
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.util.text.TextLinkifier
import java.util.Locale
import java.util.concurrent.Callable

object ExtractorHelper {
    private val TAG: String = ExtractorHelper::class.java.getSimpleName()
    private val CACHE: InfoCache = InfoCache.Companion.getInstance()
    private fun checkServiceId(serviceId: Int) {
        if (serviceId == NO_SERVICE_ID) {
            throw IllegalArgumentException("serviceId is NO_SERVICE_ID")
        }
    }

    fun searchFor(serviceId: Int, searchString: String?,
                  contentFilter: List<String>?,
                  sortFilter: String?): Single<SearchInfo?> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({
            SearchInfo.getInfo(NewPipe.getService(serviceId),
                    NewPipe.getService(serviceId)
                            .getSearchQHFactory()
                            .fromQuery(searchString, contentFilter, sortFilter))
        }))
    }

    fun getMoreSearchItems(
            serviceId: Int,
            searchString: String?,
            contentFilter: List<String>?,
            sortFilter: String?,
            page: Page?): Single<InfoItemsPage<InfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({
            SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                    NewPipe.getService(serviceId)
                            .getSearchQHFactory()
                            .fromQuery(searchString, contentFilter, sortFilter), page)
        }))
    }

    fun suggestionsFor(serviceId: Int, query: String?): Single<List<String>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({
            val extractor: SuggestionExtractor? = NewPipe.getService(serviceId)
                    .getSuggestionExtractor()
            if (extractor != null) extractor.suggestionList(query) else emptyList()
        }))
    }

    fun getStreamInfo(serviceId: Int, url: String?,
                      forceLoad: Boolean): Single<StreamInfo> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, (url)!!, InfoCache.Type.STREAM,
                Single.fromCallable(Callable({ StreamInfo.getInfo(NewPipe.getService(serviceId), url) })))
    }

    fun getChannelInfo(serviceId: Int, url: String?,
                       forceLoad: Boolean): Single<ChannelInfo?> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, (url)!!, InfoCache.Type.CHANNEL,
                Single.fromCallable(Callable({ ChannelInfo.getInfo(NewPipe.getService(serviceId), url) })))
    }

    fun getChannelTab(serviceId: Int,
                      listLinkHandler: ListLinkHandler?,
                      forceLoad: Boolean): Single<ChannelTabInfo?> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId,
                listLinkHandler!!.getUrl(), InfoCache.Type.CHANNEL_TAB,
                Single.fromCallable(Callable({ ChannelTabInfo.getInfo(NewPipe.getService(serviceId), (listLinkHandler)) })))
    }

    fun getMoreChannelTabItems(
            serviceId: Int,
            listLinkHandler: ListLinkHandler?,
            nextPage: Page?): Single<InfoItemsPage<InfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({
            ChannelTabInfo.getMoreItems(NewPipe.getService(serviceId),
                    (listLinkHandler)!!, (nextPage)!!)
        }))
    }

    fun getCommentsInfo(serviceId: Int,
                        url: String,
                        forceLoad: Boolean): Single<CommentsInfo> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.COMMENTS,
                Single.fromCallable(Callable({ CommentsInfo.getInfo(NewPipe.getService(serviceId), url) })))
    }

    fun getMoreCommentItems(
            serviceId: Int,
            info: CommentsInfo?,
            nextPage: Page?): Single<InfoItemsPage<CommentsInfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({ CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage) }))
    }

    fun getMoreCommentItems(
            serviceId: Int,
            url: String?,
            nextPage: Page?): Single<InfoItemsPage<CommentsInfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({ CommentsInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage) }))
    }

    fun getPlaylistInfo(serviceId: Int,
                        url: String?,
                        forceLoad: Boolean): Single<PlaylistInfo?> {
        checkServiceId(serviceId)
        return checkCache(forceLoad, serviceId, (url)!!, InfoCache.Type.PLAYLIST,
                Single.fromCallable(Callable({ PlaylistInfo.getInfo(NewPipe.getService(serviceId), url) })))
    }

    fun getMorePlaylistItems(serviceId: Int,
                             url: String?,
                             nextPage: Page?): Single<InfoItemsPage<StreamInfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable(Callable({ PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage) }))
    }

    fun getKioskInfo(serviceId: Int,
                     url: String,
                     forceLoad: Boolean): Single<KioskInfo> {
        return checkCache(forceLoad, serviceId, url, InfoCache.Type.KIOSK,
                Single.fromCallable(Callable({ KioskInfo.getInfo(NewPipe.getService(serviceId), url) })))
    }

    fun getMoreKioskItems(serviceId: Int,
                          url: String?,
                          nextPage: Page?): Single<InfoItemsPage<StreamInfoItem>> {
        return Single.fromCallable(Callable({ KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage) }))
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Cache
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     *
     * @param <I>             the item type's class that extends [Info]
     * @param forceLoad       whether to force loading from the network instead of from the cache
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param cacheType       the [InfoCache.Type] of the item
     * @param loadFromNetwork the [Single] to load the item from the network
     * @return a [Single] that loads the item
    </I> */
    private fun <I : Info?> checkCache(forceLoad: Boolean,
                                       serviceId: Int,
                                       url: String,
                                       cacheType: InfoCache.Type,
                                       loadFromNetwork: Single<I>): Single<I> {
        checkServiceId(serviceId)
        val actualLoadFromNetwork: Single<I> = loadFromNetwork
                .doOnSuccess(Consumer({ info: I -> CACHE.putInfo(serviceId, url, info, cacheType) }))
        val load: Single<I>
        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType)
            load = actualLoadFromNetwork
        } else {
            load = Maybe.concat<I>(loadFromCache<I>(serviceId, url, cacheType),
                    actualLoadFromNetwork.toMaybe())
                    .firstElement() // Take the first valid
                    .toSingle()
        }
        return load
    }

    /**
     * Default implementation uses the [InfoCache] to get cached results.
     *
     * @param <I>       the item type's class that extends [Info]
     * @param serviceId the service to load from
     * @param url       the URL to load
     * @param cacheType the [InfoCache.Type] of the item
     * @return a [Single] that loads the item
    </I> */
    private fun <I : Info?> loadFromCache(
            serviceId: Int,
            url: String,
            cacheType: InfoCache.Type): Maybe<I?> {
        checkServiceId(serviceId)
        return Maybe.defer<I?>(Supplier<MaybeSource<out I?>>({
            val info: I? = CACHE.getFromKey(serviceId, url, cacheType) as I?
            if (MainActivity.Companion.DEBUG) {
                Log.d(TAG, "loadFromCache() called, info > " + info)
            }

            // Only return info if it's not null (it is cached)
            if (info != null) {
                return@defer Maybe.just<I>(info)
            }
            Maybe.empty<I?>()
        }))
    }

    fun isCached(serviceId: Int,
                 url: String,
                 cacheType: InfoCache.Type): Boolean {
        return null != loadFromCache<Info?>(serviceId, url, cacheType).blockingGet()
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    /**
     * Formats the text contained in the meta info list as HTML and puts it into the text view,
     * while also making the separator visible. If the list is null or empty, or the user chose not
     * to see meta information, both the text view and the separator are hidden
     *
     * @param metaInfos         a list of meta information, can be null or empty
     * @param metaInfoTextView  the text view in which to show the formatted HTML
     * @param metaInfoSeparator another view to be shown or hidden accordingly to the text view
     * @param disposables       disposables created by the method are added here and their lifecycle
     * should be handled by the calling class
     */
    fun showMetaInfoInTextView(metaInfos: List<MetaInfo>?,
                               metaInfoTextView: TextView,
                               metaInfoSeparator: View,
                               disposables: CompositeDisposable) {
        val context: Context = metaInfoTextView.getContext()
        if (((metaInfos == null) || metaInfos.isEmpty()
                        || !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                        context.getString(R.string.show_meta_info_key), true))) {
            metaInfoTextView.setVisibility(View.GONE)
            metaInfoSeparator.setVisibility(View.GONE)
        } else {
            val stringBuilder: StringBuilder = StringBuilder()
            for (metaInfo: MetaInfo in metaInfos) {
                if (!Utils.isNullOrEmpty(metaInfo.getTitle())) {
                    stringBuilder.append("<b>").append(metaInfo.getTitle()).append("</b>")
                            .append(Localization.DOT_SEPARATOR)
                }
                var content: String = metaInfo.getContent().getContent().trim({ it <= ' ' })
                if (content.endsWith(".")) {
                    content = content.substring(0, content.length - 1) // remove . at end
                }
                stringBuilder.append(content)
                for (i in metaInfo.getUrls().indices) {
                    if (i == 0) {
                        stringBuilder.append(Localization.DOT_SEPARATOR)
                    } else {
                        stringBuilder.append("<br/><br/>")
                    }
                    stringBuilder
                            .append("<a href=\"").append(metaInfo.getUrls().get(i)).append("\">")
                            .append(capitalizeIfAllUppercase(metaInfo.getUrlTexts().get(i).trim({ it <= ' ' })))
                            .append("</a>")
                }
            }
            metaInfoSeparator.setVisibility(View.VISIBLE)
            TextLinkifier.fromHtml(metaInfoTextView, stringBuilder.toString(),
                    HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING, null, null, disposables,
                    TextLinkifier.SET_LINK_MOVEMENT_METHOD)
        }
    }

    private fun capitalizeIfAllUppercase(text: String): String {
        for (i in 0 until text.length) {
            if (Character.isLowerCase(text.get(i))) {
                return text // there is at least a lowercase letter -> not all uppercase
            }
        }
        if (text.isEmpty()) {
            return text
        } else {
            return text.substring(0, 1).uppercase(Locale.getDefault()) + text.substring(1).lowercase(Locale.getDefault())
        }
    }
}
