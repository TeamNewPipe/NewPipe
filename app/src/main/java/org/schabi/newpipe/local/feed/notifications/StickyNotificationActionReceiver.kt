/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.PendingIntentCompat
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.profiles.ProfileManager

/** Only explicit notification actions acknowledge a pending alert. Opening it never does. */
class StickyNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val target = StickyNotificationTarget.fromIntent(intent) ?: return
        val close = when (target.action) {
            StickyNotificationAction.CLOSE_ITEM -> StickyNotificationCoordinator.closeItem(
                context.applicationContext,
                target.profileId,
                target.generation,
                target.serviceId,
                target.url
            )

            StickyNotificationAction.CLOSE_CHANNEL -> StickyNotificationCoordinator.closeChannel(
                context.applicationContext,
                target.profileId,
                target.generation,
                target.serviceId,
                target.url
            )

            StickyNotificationAction.CLOSE_ALL -> StickyNotificationCoordinator.closeAll(
                context.applicationContext,
                target.profileId,
                target.generation
            )

            else -> return
        }
        val result = goAsync()
        close.subscribeOn(Schedulers.io()).subscribe(
            { result.finish() },
            {
                // Do not include channel URLs, titles or the database exception in logs.
                Log.w("StickyNotifications", "Could not close pending notification")
                result.finish()
            }
        )
    }
}

internal enum class StickyNotificationAction(val key: String) {
    OPEN_ITEM("open-item"),
    OPEN_CHANNEL("open-channel"),
    OPEN_FEED("open-feed"),
    CLOSE_ITEM("close-item"),
    CLOSE_CHANNEL("close-channel"),
    CLOSE_ALL("close-all")
}

/** Identity lives in Intent.data, which PendingIntent compares; extras are only display metadata. */
internal data class StickyNotificationTarget(
    val action: StickyNotificationAction,
    val profileId: String,
    val generation: String,
    val serviceId: Int = -1,
    val url: String = ""
) {
    fun uri(): Uri = Uri.Builder()
        .scheme("wizestream")
        .authority("sticky-notification")
        .appendPath(profileId)
        .appendPath(generation)
        .appendPath(action.key)
        .apply {
            if (action != StickyNotificationAction.OPEN_FEED && action != StickyNotificationAction.CLOSE_ALL) {
                appendPath(serviceId.toString())
                appendPath(url)
            }
        }
        .build()

    fun contentIntent(context: Context, title: String? = null): PendingIntent = checkNotNull(
        PendingIntentCompat.getActivity(
            context,
            0,
            Intent(context, StickyNotificationActivity::class.java)
                .setAction(INTENT_ACTION)
                .setData(uri())
                .putExtra(TITLE_EXTRA, title),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
    )

    fun closeIntent(context: Context): PendingIntent = checkNotNull(
        PendingIntentCompat.getBroadcast(
            context,
            0,
            Intent(context, StickyNotificationActionReceiver::class.java)
                .setAction(INTENT_ACTION)
                .setData(uri()),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
    )

    companion object {
        const val TITLE_EXTRA = "sticky_notification_title"
        private const val INTENT_ACTION = "org.schabi.newpipe.STICKY_NOTIFICATION"

        fun fromIntent(intent: Intent): StickyNotificationTarget? = if (intent.action == INTENT_ACTION) intent.data?.let(::fromUri) else null

        fun fromUri(uri: Uri): StickyNotificationTarget? {
            if (uri.scheme != "wizestream" || uri.authority != "sticky-notification" ||
                uri.query != null || uri.fragment != null
            ) {
                return null
            }
            val segments = uri.pathSegments
            if (segments.size != 3 && segments.size != 5) return null
            val profile = segments[0].takeIf(ProfileManager::isCanonicalProfileId) ?: return null
            val generation = segments[1].takeIf { it.isNotBlank() } ?: return null
            val action = StickyNotificationAction.entries.firstOrNull { it.key == segments[2] } ?: return null
            val all = action == StickyNotificationAction.OPEN_FEED || action == StickyNotificationAction.CLOSE_ALL
            if (all != (segments.size == 3)) return null
            val target = if (all) {
                StickyNotificationTarget(action, profile, generation)
            } else {
                val serviceId = segments[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val url = segments[4].takeIf { it.isNotBlank() } ?: return null
                StickyNotificationTarget(action, profile, generation, serviceId, url)
            }
            return target.takeIf { it.uri() == uri }
        }
    }
}
