package org.schabi.newpipe.util.text

import android.content.Context
import androidx.core.content.ContextCompat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.player.TimestampChangeData
import org.schabi.newpipe.util.NavigationHelper
import java.util.regex.Pattern

object InternalUrlsHandler {
    private val AMPERSAND_TIMESTAMP_PATTERN = Pattern.compile("(.*)&t=(\\d+)")

    /**
     * Handle a YouTube timestamp description URL in NewPipe.
     *
     * This method will check if the provided url is a YouTube timestamp description URL (`
     * https://www.youtube.com/watch?v=`video_id`&t=`time_in_seconds). If yes, the popup
     * player will be opened when the user will click on the timestamp in the video description,
     * at the time and for the video indicated in the timestamp.
     *
     * @param context     the context to use
     * @param url         the URL to check if it can be handled
     * @return true if the URL can be handled by NewPipe, false if it cannot
     */
    @JvmStatic
    fun handleUrlDescriptionTimestamp(context: Context, url: String): Boolean {
        val matcher = AMPERSAND_TIMESTAMP_PATTERN.matcher(url)
        if (!matcher.matches()) {
            return false
        }
        val matchedUrl = matcher.group(1) ?: return false
        val seconds = matcher.group(2)?.toInt() ?: -1

        val service: StreamingService
        val linkType: StreamingService.LinkType
        try {
            service = NewPipe.getServiceByUrl(matchedUrl)
            linkType = service.getLinkTypeByUrl(matchedUrl)
            if (linkType == StreamingService.LinkType.NONE) {
                return false
            }
        } catch (e: ExtractionException) {
            return false
        }

        return if (linkType == StreamingService.LinkType.STREAM && seconds != -1) {
            playOnPopup(context, matchedUrl, service, seconds)
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
     * @return true if the playback of the content has successfully started or false if not
     */
    @JvmStatic
    fun playOnPopup(
        context: Context,
        url: String,
        service: StreamingService,
        seconds: Int
    ): Boolean {
        val factory = service.streamLHFactory
        val cleanUrl: String = try {
            factory.getUrl(factory.getId(url))
        } catch (e: ParsingException) {
            return false
        }

        val intent = NavigationHelper.getPlayerTimestampIntent(
            context,
            TimestampChangeData(
                service.serviceId,
                cleanUrl,
                seconds
            )
        )
        ContextCompat.startForegroundService(context, intent)

        return true
    }
}
