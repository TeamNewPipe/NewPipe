/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.image.ImageStrategy

/** A bounded projection of durable pending alerts; this class never removes stored records. */
object StickyNotificationRenderer {
    private const val TAG_PREFIX = "wizestream.sticky."
    private const val NOTIFICATION_ID = 0
    internal const val MAX_VISIBLE_STREAMS = 20
    private const val MAX_INBOX_LINES = 6

    /** Run outside the coordinator monitor: a slow image must never hold up a Close broadcast. */
    fun prepareArtwork(
        context: Context,
        profileId: String,
        pending: List<PendingStreamNotification>,
        alertChannels: Set<Pair<Int, String>>
    ): Map<Pair<Int, String>, Bitmap> {
        val generation = StickyNotificationSettings.generation(context)
        if (alertChannels.isEmpty() || !showImages(context) || !canPost(context, profileId, generation)) return emptyMap()
        val images = NotificationImages(context)
        val artwork = mutableMapOf<Pair<Int, String>, Bitmap>()
        currentEntries(profileId, generation, pending).take(MAX_VISIBLE_STREAMS).forEach { entry ->
            if (!canPost(context, profileId, generation)) return emptyMap()
            if (entry.serviceId to entry.channelUrl in alertChannels) {
                images.load(entry.thumbnailUrl, 640, 360)?.let { artwork[entry.serviceId to entry.streamUrl] = it }
            }
        }
        return if (canPost(context, profileId, generation)) artwork else emptyMap()
    }

    /** Consumes prepared bitmaps only; callers re-read pending records after artwork completes. */
    fun render(
        context: Context,
        profileId: String,
        pending: List<PendingStreamNotification>,
        silent: Boolean,
        alertChannels: Set<Pair<Int, String>> = emptySet(),
        artwork: Map<Pair<Int, String>, Bitmap> = emptyMap()
    ) {
        val generation = StickyNotificationSettings.generation(context)
        if (!canPost(context, profileId, generation)) {
            cancelAll(context)
            return
        }
        val entries = currentEntries(profileId, generation, pending)
        val visible = entries.take(MAX_VISIBLE_STREAMS)
        val groups = entries.groupBy { it.serviceId to it.channelUrl }
        val visibleGroups = visible.mapTo(linkedSetOf()) { it.serviceId to it.channelUrl }
        val notifications = linkedMapOf<String, Notification>()
        val showImages = showImages(context)
        visible.forEach { entry ->
            val target = entry.target(StickyNotificationAction.OPEN_ITEM)
            val close = entry.target(StickyNotificationAction.CLOSE_ITEM).closeIntent(context)
            val builder = base(context, entry.createdAt, true)
                .setContentTitle(entry.streamTitle)
                .setContentText(entry.channelName)
                .setSubText(serviceName(context, entry.serviceId))
                .setGroup(group(entry))
                .setContentIntent(target.contentIntent(context, entry.streamTitle))
                .setDeleteIntent(close)
                .setStyle(NotificationCompat.BigTextStyle().bigText(entry.streamTitle))
                .addAction(0, context.getString(R.string.sticky_notification_close), close)
            // Restore/close pass no bitmaps. Even a normal render never starts an image request
            // while the coordinator is serializing pending records and acknowledgement.
            if (showImages) {
                artwork[entry.serviceId to entry.streamUrl]?.let {
                    builder.setLargeIcon(it).setStyle(
                        NotificationCompat.BigPictureStyle().bigPicture(it).bigLargeIcon(null as Bitmap?)
                    )
                }
            }
            notifications[tag(target)] = sticky(builder)
        }
        visibleGroups.forEach { key ->
            val channelEntries = groups.getValue(key)
            val entry = channelEntries.first()
            val count = context.resources.getQuantityString(R.plurals.new_streams, channelEntries.size, channelEntries.size)
            val target = entry.target(StickyNotificationAction.OPEN_CHANNEL)
            val close = entry.target(StickyNotificationAction.CLOSE_CHANNEL).closeIntent(context)
            val closeAll = StickyNotificationTarget(StickyNotificationAction.CLOSE_ALL, profileId, generation).closeIntent(context)
            val inbox = NotificationCompat.InboxStyle().setBigContentTitle(entry.channelName).setSummaryText(count)
            channelEntries.take(MAX_INBOX_LINES).forEach { inbox.addLine(it.streamTitle) }
            val alert = !silent && key in alertChannels
            val builder = base(context, entry.createdAt, !alert)
                .setOnlyAlertOnce(!alert)
                .setContentTitle(entry.channelName)
                .setContentText(count)
                .setSubText(serviceName(context, entry.serviceId))
                .setNumber(channelEntries.size)
                .setStyle(inbox)
                .setGroup(group(entry))
                .setGroupSummary(true)
                .setContentIntent(target.contentIntent(context))
                .setDeleteIntent(close)
                .addAction(0, context.getString(R.string.sticky_notification_close_channel), close)
                .addAction(0, context.getString(R.string.sticky_notification_close_all), closeAll)
            notifications[tag(target)] = sticky(builder)
        }
        val hidden = groups.filterKeys { it !in visibleGroups }.values.sumOf { it.size }
        if (hidden > 0) {
            val target = StickyNotificationTarget(StickyNotificationAction.OPEN_FEED, profileId, generation)
            val closeAll = target.copy(action = StickyNotificationAction.CLOSE_ALL).closeIntent(context)
            val count = context.resources.getQuantityString(R.plurals.new_streams, entries.size, entries.size)
            val hiddenCount = context.resources.getQuantityString(R.plurals.sticky_notification_streams_in_other_channels, hidden, hidden)
            notifications[tag(target)] = sticky(
                base(context, entries.first().createdAt, true)
                    .setContentTitle(context.getString(R.string.sticky_notification_all_pending))
                    .setContentText(count)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$count\n$hiddenCount"))
                    .setNumber(entries.size)
                    .setContentIntent(target.contentIntent(context))
                    .setDeleteIntent(closeAll)
                    .addAction(0, context.getString(R.string.sticky_notification_close_all), closeAll)
            )
        }
        // Artwork decoding can outlive a profile switch or off/on toggle. Recheck before every
        // post, not just when this projection was requested by the coordinator.
        val manager = context.getSystemService(NotificationManager::class.java)
        cancelExcept(manager, notifications.keys)
        notifications.forEach { (tag, notification) ->
            if (!canPost(context, profileId, generation)) {
                cancelAll(context)
                return
            }
            manager.notify(tag, NOTIFICATION_ID, notification)
        }
    }

    fun cancelProfile(context: Context, profileId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter {
            it.tag?.takeIf { tag -> tag.startsWith(TAG_PREFIX) }
                ?.removePrefix(TAG_PREFIX)?.toUri()?.let(StickyNotificationTarget::fromUri)?.profileId == profileId
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun cancelAll(context: Context) = cancelExcept(context.getSystemService(NotificationManager::class.java), emptySet())

    private fun cancelExcept(manager: NotificationManager, desired: Set<String>) {
        manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true && it.tag !in desired }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun canPost(context: Context, profileId: String, generation: String): Boolean = NotificationHelper.areNewStreamsNotificationsEnabled(context) &&
        StickyNotificationCoordinator.canOpen(context, profileId, generation)

    private fun showImages(context: Context): Boolean = ImageStrategy.shouldLoadImages() && PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(context.getString(R.string.streams_notifications_thumbnails_key), true)

    private fun currentEntries(profileId: String, generation: String, pending: List<PendingStreamNotification>): List<PendingStreamNotification> = pending.asSequence()
        .filter { it.profileId == profileId && it.generation == generation && !it.dismissed }
        .sortedWith(compareByDescending<PendingStreamNotification> { it.createdAt }.thenBy { it.serviceId }.thenBy { it.streamUrl })
        .distinctBy { it.serviceId to it.streamUrl }
        .toList()

    private fun base(context: Context, createdAt: Long, silent: Boolean): NotificationCompat.Builder = NotificationCompat.Builder(context, context.getString(R.string.streams_notification_channel_id))
        .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
        .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setWhen(createdAt)
        .setShowWhen(true)
        .setAutoCancel(false)
        .setOnlyAlertOnce(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        .setSilent(silent)

    private fun sticky(builder: NotificationCompat.Builder): Notification = builder.build().apply {
        flags = flags or Notification.FLAG_NO_CLEAR
    }

    private fun group(entry: PendingStreamNotification): String = tag(entry.target(StickyNotificationAction.OPEN_CHANNEL))

    private fun tag(target: StickyNotificationTarget): String = TAG_PREFIX + target.uri()

    private fun PendingStreamNotification.target(action: StickyNotificationAction): StickyNotificationTarget = StickyNotificationTarget(
        action,
        profileId,
        generation,
        serviceId,
        if (action == StickyNotificationAction.OPEN_CHANNEL || action == StickyNotificationAction.CLOSE_CHANNEL) channelUrl else streamUrl
    )

    private fun serviceName(context: Context, serviceId: Int): String = ServiceList.all().firstOrNull { it.serviceId == serviceId }
        ?.serviceInfo?.name ?: context.getString(R.string.sticky_notification_service, serviceId)
}
