/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.function.Supplier
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.profiles.ProfileManager

/**
 * Inherit Channel Group icons for Home tabs, unless the user has selected a custom tab icon.
 * Room observation reacts to local edits and synchronized group changes without UI-thread queries.
 */
object HomeTabGroupIconSync {
    private const val TAG = "HomeTabGroupIconSync"

    @JvmStatic
    fun observe(context: Context, tabs: Supplier<List<Tab>>, onChanged: Runnable): Disposable {
        val profileId = ProfileManager.getActiveProfileId(context)
        return NewPipeDatabase.getInstance(context).feedGroupDAO()
            .getAllForProfile(profileId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { groups ->
                    if (apply(tabs.get(), groups)) onChanged.run()
                },
                { error -> Log.w(TAG, "Unable to observe Channel Group icons", error) }
            )
    }

    internal fun apply(tabs: List<Tab>, groups: List<FeedGroupEntity>): Boolean {
        val icons = groups.associate { it.uid to it.icon.drawableResource }
        var updated = false
        tabs.forEach { tab ->
            if (tab is Tab.FeedGroupTab) {
                val icon = tab.feedGroupId?.let { icons[it] } ?: Tab.DEFAULT_ICON_ID
                if (tab.setObservedGroupIcon(icon)) updated = true
            }
        }
        return updated
    }
}
