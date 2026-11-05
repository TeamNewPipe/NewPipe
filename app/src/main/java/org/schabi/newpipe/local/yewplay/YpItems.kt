/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.content.Context
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpError
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * YewPlay: turns the extractor's items into the models of the Compose lists, with the same
 * texts the previous list items showed.
 */
object YpItems {

    /** Whether a list can show this item (videos, channels and playlists). */
    fun isSupported(item: InfoItem): Boolean {
        return item is StreamInfoItem || item is ChannelInfoItem || item is PlaylistInfoItem
    }

    /**
     * Maps an item of a list.
     *
     * @param context for the localized texts
     * @param item a video, channel or playlist (see [isSupported])
     * @param showUploader false inside a channel, where the channel is not repeated
     * @return the item for the Compose list
     */
    fun toUi(context: Context, item: InfoItem, showUploader: Boolean = true): YpListItem = when (item) {
        is StreamInfoItem -> stream(context, item, showUploader)

        is ChannelInfoItem -> YpChannelItem(
            serviceId = item.serviceId,
            url = item.url,
            name = item.name,
            avatarUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            description = item.description?.takeIf { it.isNotBlank() },
            details = channelDetails(context, item)
        )

        else -> {
            val playlist = item as PlaylistInfoItem
            YpPlaylistItem(
                serviceId = playlist.serviceId,
                url = playlist.url,
                name = playlist.name,
                thumbnailUrl = ImageStrategy.choosePreferredImage(playlist.thumbnails),
                uploaderName = playlist.uploaderName,
                countText = Localization.localizeStreamCountMini(context, playlist.streamCount)
            )
        }
    }

    private fun stream(context: Context, item: StreamInfoItem, showUploader: Boolean): YpStreamItem {
        val live = StreamTypeUtil.isLiveStream(item.streamType)
        return YpStreamItem(
            serviceId = item.serviceId,
            url = item.url,
            title = item.name,
            uploaderName = if (showUploader) item.uploaderName.orEmpty() else "",
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            durationText = when {
                item.duration > 0 -> Localization.getDurationString(item.duration)
                live -> context.getString(R.string.duration_live)
                else -> null
            },
            isLive = live,
            details = streamDetails(context, item)
        )
    }

    private fun streamDetails(context: Context, item: StreamInfoItem): String {
        val views = when {
            item.viewCount < 0 -> ""

            item.streamType == StreamType.AUDIO_LIVE_STREAM ->
                Localization.listeningCount(context, item.viewCount)

            item.streamType == StreamType.LIVE_STREAM ->
                Localization.shortWatchingCount(context, item.viewCount)

            else -> Localization.shortViewCount(context, item.viewCount)
        }
        val date = Localization
            .relativeTimeOrTextual(context, item.uploadDate, item.textualUploadDate)
            .orEmpty()
        return when {
            date.isEmpty() -> views
            views.isEmpty() -> date
            else -> Localization.concatenateStrings(views, date)
        }
    }

    private fun channelDetails(context: Context, item: ChannelInfoItem): String? {
        val subscribers = item.subscriberCount.takeIf { it >= 0 }
            ?.let { Localization.shortSubscriberCount(context, it) }
        val streams = item.streamCount.takeIf { it >= 0 }
            ?.let { Localization.localizeStreamCount(context, it) }
        return when {
            subscribers != null && streams != null ->
                Localization.concatenateStrings(subscribers, streams)

            else -> subscribers ?: streams
        }
    }

    /**
     * Watched fraction of the videos that have a saved position, when positions are shown in
     * lists (same setting as before).
     *
     * @param context for the setting
     * @param manager the history
     * @param items items of a list
     * @return the fraction from 0 to 1 by video URL
     */
    fun loadProgress(
        context: Context,
        manager: HistoryRecordManager,
        items: List<InfoItem>
    ): Single<Map<String, Float>> {
        val streams = items.filterIsInstance<StreamInfoItem>().filter { it.duration > 0 }
        if (streams.isEmpty() || !DependentPreferenceHelper.getPositionsInListsEnabled(context)) {
            return Single.just(emptyMap())
        }
        return Single.fromCallable {
            streams.mapNotNull { stream ->
                manager.loadStreamState(stream).blockingGet()?.let { state ->
                    val fraction = state.progressMillis / 1000f / stream.duration
                    stream.url to fraction.coerceIn(0f, 1f)
                }
            }.toMap()
        }.subscribeOn(Schedulers.io())
    }

    /**
     * Whether each channel of a list is in the local subscriptions.
     *
     * @param manager the subscriptions
     * @param items items of a list
     * @return subscribed or not by channel URL
     */
    fun loadSubscribed(
        manager: SubscriptionManager,
        items: List<InfoItem>
    ): Single<Map<String, Boolean>> {
        val channels = items.filterIsInstance<ChannelInfoItem>()
        if (channels.isEmpty()) {
            return Single.just(emptyMap())
        }
        return Single.fromCallable {
            channels.associate { channel ->
                channel.url to manager.subscriptionTable()
                    .getSubscriptionFlowable(channel.serviceId, channel.url)
                    .blockingFirst()
                    .isNotEmpty()
            }
        }.subscribeOn(Schedulers.io())
    }

    /** Applies watched fractions to the videos of a list. */
    fun withProgress(items: List<YpListItem>, progress: Map<String, Float>): List<YpListItem> {
        if (progress.isEmpty()) {
            return items
        }
        return items.map { item ->
            val fraction = progress[item.url]
            if (item is YpStreamItem && fraction != null) item.copy(progress = fraction) else item
        }
    }

    /**
     * The subscription saved when subscribing from a list.
     *
     * @param item the channel on screen
     * @param channel the extractor item behind it, for avatar, description and count
     * @return the entity to insert
     */
    fun subscriptionFor(item: YpChannelItem, channel: ChannelInfoItem?) = SubscriptionEntity(
        serviceId = item.serviceId,
        url = item.url,
        name = item.name,
        avatarUrl = channel?.let { ImageStrategy.imageListToDbUrl(it.thumbnails) },
        subscriberCount = channel?.subscriberCount?.takeIf { it >= 0 },
        description = channel?.description
    )

    /**
     * An error for the Compose error state, with the same buttons as the previous error panel.
     *
     * @param context for the texts
     * @param info the error
     * @return the error to show
     */
    fun errorUi(context: Context, info: ErrorInfo): YpError = YpError(
        message = info.getMessage(context).toString(),
        actionLabel = when {
            info.recaptchaUrl != null -> context.getString(R.string.recaptcha_solve)
            info.isReportable -> context.getString(R.string.error_snackbar_action)
            else -> null
        },
        canRetry = info.isRetryable,
        canOpenInBrowser = info.openInBrowserUrl != null
    )
}
