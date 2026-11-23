/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.comments

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/** A request may archive only for the profile and privacy settings that initiated it. */
class CommentArchiveRequest private constructor(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(app)
    private val archiveEnabledKey = app.getString(R.string.comment_archive_enabled_key)
    private val invalidated = AtomicBoolean()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY ||
            key == IncognitoMode.KEY || key == archiveEnabledKey
        ) {
            // Keep the request invalid even if the user switches back before it completes.
            invalidated.set(true)
        }
    }
    private val profileId: String
    private val enabledAtStart: Boolean

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        profileId = ProfileManager.getActiveProfileId(app)
        enabledAtStart = CommentArchive.isEnabled(app)
    }

    fun record(serviceId: Int, url: String, comments: List<CommentsInfoItem>, parentId: String?) {
        if (!enabledAtStart || invalidated.get()) return
        CommentArchive.recordForProfile(app, profileId, serviceId, url, comments, parentId)
    }

    override fun close() {
        invalidated.set(true)
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        @JvmStatic
        fun capture(context: Context): CommentArchiveRequest = CommentArchiveRequest(context)
    }
}
