/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SavedServicePolicy
import org.schabi.newpipe.util.TvRemoteAction
import org.schabi.newpipe.util.TvRemoteKeyDispatcher

/** Direct Activity PendingIntent entry point, rather than a broadcast/service trampoline. */
class StickyNotificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = StickyNotificationTarget.fromIntent(intent)
        if (savedInstanceState == null && target != null &&
            StickyNotificationCoordinator.canOpen(this, target.profileId, target.generation)
        ) {
            val destination = destination(target)
            if (destination != null && StickyNotificationCoordinator.canOpen(this, target.profileId, target.generation)) {
                startActivity(destination)
            }
        }
        finish()
    }

    private fun destination(target: StickyNotificationTarget): Intent? {
        if (target.action == StickyNotificationAction.OPEN_FEED) {
            return Intent(this, MainActivity::class.java)
                .putExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION, TvRemoteAction.FEED.id)
        }
        val registered = ServiceList.all().mapTo(mutableSetOf()) { it.serviceId }
        if (!SavedServicePolicy.canRefresh(target.serviceId, registered, PluginManager.loadedProfileId, target.profileId)) return null
        return when (target.action) {
            StickyNotificationAction.OPEN_ITEM -> NavigationHelper.getStreamIntent(
                this,
                target.serviceId,
                target.url,
                intent.getStringExtra(StickyNotificationTarget.TITLE_EXTRA)
            )

            StickyNotificationAction.OPEN_CHANNEL -> NavigationHelper.getChannelIntent(this, target.serviceId, target.url)

            else -> null
        }
    }
}
