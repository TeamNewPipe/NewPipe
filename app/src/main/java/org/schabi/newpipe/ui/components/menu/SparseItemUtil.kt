package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.StreamTypeUtil

// Utilities for fetching additional data for stream items when needed.

/**
 * Use this to certainly obtain an single play queue with all of the data filled in when the
 * stream info item you are handling might be sparse, e.g. because it was fetched via a
 * [org.schabi.newpipe.extractor.feed.FeedExtractor]. FeedExtractors provide a fast and
 * lightweight method to fetch info, but the info might be incomplete (see
 * [org.schabi.newpipe.local.feed.service.FeedLoadService] for more details).
 *
 * @param context  Android context
 * @param item     item which is checked and eventually loaded completely
 * @return a [SinglePlayQueue] with full data (fetched if necessary)
 */
@MainThread
suspend fun fetchItemInfoIfSparse(
    context: Context,
    item: StreamInfoItem,
): SinglePlayQueue {
    if ((StreamTypeUtil.isLiveStream(item.streamType) || item.duration >= 0) &&
        !Utils.isNullOrEmpty(item.uploaderUrl)
    ) {
        // if the duration is >= 0 (provided that the item is not a livestream) and there is an
        // uploader url, probably all info is already there, so there is no need to fetch it
        return SinglePlayQueue(item)
    }

    // either the duration or the uploader url are not available, so fetch more info
    val streamInfo = fetchStreamInfoAndSaveToDatabase(context, item.serviceId, item.url)
    return SinglePlayQueue(streamInfo)
}

/**
 * Use this to certainly obtain an uploader url when the stream info item or play queue item you
 * are handling might not have the uploader url (e.g. because it was fetched with
 * [org.schabi.newpipe.extractor.feed.FeedExtractor]). A toast is shown if loading details is
 * required.
 *
 * @param context     Android context
 * @param serviceId   serviceId of the item
 * @param url         item url
 * @param uploaderUrl uploaderUrl of the item; if null or empty will be fetched
 * @return the original or the fetched uploader URL (may still be null if the extractor didn't
 * provide one)
 */
@MainThread
suspend fun fetchUploaderUrlIfSparse(
    context: Context,
    serviceId: Int,
    url: String,
    uploaderUrl: String?,
): String? {
    if (!uploaderUrl.isNullOrEmpty()) {
        return uploaderUrl
    }
    val streamInfo = fetchStreamInfoAndSaveToDatabase(context, serviceId, url)
    return streamInfo.uploaderUrl
}

/**
 * Loads the stream info corresponding to the given data on an I/O thread, stores the result in
 * the database, and returns. A toast will be shown to the user about loading stream details, so
 * this needs to be called on the main thread.
 *
 * @param context   Android context
 * @param serviceId service id of the stream to load
 * @param url       url of the stream to load
 * @return the fetched [StreamInfo]
 */
@MainThread
suspend fun fetchStreamInfoAndSaveToDatabase(
    context: Context,
    serviceId: Int,
    url: String,
): StreamInfo {
    Toast.makeText(context, R.string.loading_stream_details, Toast.LENGTH_SHORT).show()

    return withContext(Dispatchers.IO) {
        val streamInfo = ExtractorHelper.getStreamInfo(serviceId, url, false)
            .subscribeOn(Schedulers.io())
            .await()
        // save to database
        NewPipeDatabase.getInstance(context)
            .streamDAO()
            .upsert(StreamEntity(streamInfo))
        return@withContext streamInfo
    }
}
