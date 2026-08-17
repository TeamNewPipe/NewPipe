/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * SparseItemUtil.kt is part of NewPipe
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
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/**
 * Utility class for fetching additional data for stream items when needed.
 */
object SparseItemUtil {
    /**
     * Use this to certainly obtain an single play queue with all of the data filled in when the
     * stream info item you are handling might be sparse, e.g. because it was fetched via a [
     * org.schabi.newpipe.extractor.feed.FeedExtractor]. FeedExtractors provide a fast and
     * lightweight method to fetch info, but the info might be incomplete (see
     * [org.schabi.newpipe.local.feed.service.FeedLoadService] for more details).
     *
     * @param context  Android context
     * @param scope    CoroutineScope to launch the fetch operation
     * @param item     item which is checked and eventually loaded completely
     * @param callback callback to call with the single play queue built from the original item if
     * all info was available, otherwise from the fetched [
     * org.schabi.newpipe.extractor.stream.StreamInfo]
     */
    fun fetchItemInfoIfSparse(
        context: Context,
        scope: CoroutineScope,
        item: StreamInfoItem,
        callback: (SinglePlayQueue) -> Unit
    ) {
        if ((StreamTypeUtil.isLiveStream(item.streamType) || item.duration >= 0) &&
            !item.uploaderUrl.isNullOrEmpty()
        ) {
            callback(SinglePlayQueue(item))
            return
        }

        fetchStreamInfoAndSaveToDatabase(context, scope, item.serviceId, item.url, callback = {
            callback(SinglePlayQueue(it))
        })
    }

    /**
     * Use this to certainly obtain an uploader url when the stream info item or play queue item you
     * are handling might not have the uploader url (e.g. because it was fetched with [
     * org.schabi.newpipe.extractor.feed.FeedExtractor]). A toast is shown if loading details is
     * required.
     *
     * @param context     Android context
     * @param scope       CoroutineScope to launch the fetch operation
     * @param serviceId   serviceId of the item
     * @param url         item url
     * @param uploaderUrl uploaderUrl of the item; if null or empty will be fetched
     * @param callback    callback to be called with either the original uploaderUrl, if it was a
     * valid url, otherwise with the uploader url obtained by fetching the [
     * org.schabi.newpipe.extractor.stream.StreamInfo] corresponding to the item
     */
    fun fetchUploaderUrlIfSparse(
        context: Context,
        scope: CoroutineScope,
        serviceId: Int,
        url: String,
        uploaderUrl: String?,
        callback: (String?) -> Unit
    ) {
        if (!uploaderUrl.isNullOrEmpty()) {
            callback(uploaderUrl)
            return
        }
        fetchStreamInfoAndSaveToDatabase(context, scope, serviceId, url, callback = {
            callback(it.uploaderUrl)
        })
    }

    /**
     * Loads the stream info corresponding to the given data on an I/O thread, stores the result in
     * the database and calls the callback on the main thread with the result. A toast will be shown
     * to the user about loading stream details, so this needs to be called on the main thread.
     *
     * @param context   Android context
     * @param scope     CoroutineScope to launch the fetch operation
     * @param serviceId service id of the stream to load
     * @param url       url of the stream to load
     * @param callback  callback to be called with the result
     */
    fun fetchStreamInfoAndSaveToDatabase(
        context: Context,
        scope: CoroutineScope,
        serviceId: Int,
        url: String,
        callback: (StreamInfo) -> Unit
    ) {
        Toast.makeText(context, R.string.loading_stream_details, Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val result = ExtractorHelper.getStreamInfo(serviceId, url, false)
                withContext(Dispatchers.IO) {
                    try {
                        NewPipeDatabase.getInstance(context)
                            .streamDAO().upsert(StreamEntity(result))
                    } catch (e: Exception) {
                        ErrorUtil.createNotification(
                            context,
                            ErrorInfo(
                                e, UserAction.REQUESTED_STREAM,
                                "Saving stream info to database", result
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (e: Exception) {
                ErrorUtil.createNotification(
                    context,
                    ErrorInfo(
                        e, UserAction.REQUESTED_STREAM,
                        "Loading stream info: $url", serviceId, url
                    )
                )
            }
        }
    }
}
