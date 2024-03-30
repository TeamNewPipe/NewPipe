package org.schabi.newpipe.util.text

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorPanelHelper.Companion.getExceptionDescription
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import java.util.regex.Pattern

object InternalUrlsHandler {
    private val TAG = InternalUrlsHandler::class.java.getSimpleName()
    private val DEBUG: Boolean = MainActivity.Companion.DEBUG
    private val AMPERSAND_TIMESTAMP_PATTERN = Pattern.compile("(.*)&t=(\\d+)")
    private val HASHTAG_TIMESTAMP_PATTERN = Pattern.compile("(.*)#timestamp=(\\d+)")

    /**
     * Handle a YouTube timestamp comment URL in NewPipe.
     *
     *
     * This method will check if the provided url is a YouTube comment description URL (`https://www.youtube.com/watch?v=`video_id`#timestamp=`time_in_seconds). If yes, the
     * popup player will be opened when the user will click on the timestamp in the comment,
     * at the time and for the video indicated in the timestamp.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    fun handleUrlCommentsTimestamp(disposables: CompositeDisposable,
                                   context: Context,
                                   url: String): Boolean {
        return handleUrl(context, url, HASHTAG_TIMESTAMP_PATTERN, disposables)
    }

    /**
     * Handle a YouTube timestamp description URL in NewPipe.
     *
     *
     * This method will check if the provided url is a YouTube timestamp description URL (`https://www.youtube.com/watch?v=`video_id`&t=`time_in_seconds). If yes, the popup
     * player will be opened when the user will click on the timestamp in the video description,
     * at the time and for the video indicated in the timestamp.
     *
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    fun handleUrlDescriptionTimestamp(disposables: CompositeDisposable,
                                      context: Context,
                                      url: String): Boolean {
        return handleUrl(context, url, AMPERSAND_TIMESTAMP_PATTERN, disposables)
    }

    /**
     * Handle an URL in NewPipe.
     *
     *
     * This method will check if the provided url can be handled in NewPipe or not. If this is a
     * service URL with a timestamp, the popup player will be opened and true will be returned;
     * else, false will be returned.
     *
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @param pattern     the pattern to use
     * @param disposables a field of the Activity/Fragment class that calls this method
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    private fun handleUrl(context: Context,
                          url: String,
                          pattern: Pattern,
                          disposables: CompositeDisposable): Boolean {
        val matcher = pattern.matcher(url)
        if (!matcher.matches()) {
            return false
        }
        val matchedUrl = matcher.group(1)
        val seconds: Int
        seconds = if (matcher.group(2) == null) {
            -1
        } else {
            matcher.group(2).toInt()
        }
        val service: StreamingService
        val linkType: LinkType
        try {
            service = NewPipe.getServiceByUrl(matchedUrl)
            linkType = service.getLinkTypeByUrl(matchedUrl)
            if (linkType == LinkType.NONE) {
                return false
            }
        } catch (e: ExtractionException) {
            return false
        }
        return if (linkType == LinkType.STREAM && seconds != -1) {
            playOnPopup(context, matchedUrl, service, seconds, disposables)
        } else {
            NavigationHelper.openRouterActivity(context, matchedUrl)
            true
        }
    }

    /**
     * Play a content in the floating player.
     *
     * @param context     the context to be used
     * @param url         the URL of the content
     * @param service     the service of the content
     * @param seconds     the position in seconds at which the floating player will start
     * @param disposables disposables created by the method are added here and their lifecycle
     * should be handled by the calling class
     * @return true if the playback of the content has successfully started or false if not
     */
    fun playOnPopup(context: Context?,
                    url: String,
                    service: StreamingService,
                    seconds: Int,
                    disposables: CompositeDisposable): Boolean {
        val factory = service.streamLHFactory
        val cleanUrl: String
        cleanUrl = try {
            factory.getUrl(factory.getId(url))
        } catch (e: ParsingException) {
            return false
        }
        val single: Single<StreamInfo?>? = ExtractorHelper.getStreamInfo(service.serviceId, cleanUrl, false)
        disposables.add(single!!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ info: StreamInfo? ->
                    val playQueue: PlayQueue = SinglePlayQueue(info!!, seconds * 1000L)
                    NavigationHelper.playOnPopupPlayer(context, playQueue, false)
                }) { throwable: Throwable? ->
                    if (DEBUG) {
                        Log.e(TAG, "Could not play on popup: $url", throwable)
                    }
                    AlertDialog.Builder(context!!)
                            .setTitle(R.string.player_stream_failure)
                            .setMessage(
                                    getExceptionDescription(throwable))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                })
        return true
    }
}
