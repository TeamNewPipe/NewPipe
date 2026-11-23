/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.comments

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

class CommentArchiveRequestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val archiveKey = context.getString(R.string.comment_archive_enabled_key)
    private val requests = mutableListOf<CommentArchiveRequest>()
    private val profiles = mutableListOf<String>()
    private lateinit var originalProfile: String
    private var originalArchive: Boolean? = null
    private var originalIncognito: Boolean? = null

    @Before
    fun prepareIsolatedProfile() = onMain {
        originalProfile = ProfileManager.getActiveProfileId(context)
        originalArchive = preferences.all[archiveKey] as? Boolean
        originalIncognito = preferences.all[IncognitoMode.KEY] as? Boolean
        createProfile()
        preferences.edit().putBoolean(archiveKey, true).putBoolean(IncognitoMode.KEY, false).commit()
    }

    @After
    fun restoreProfileAndSettings() = onMain {
        requests.forEach(CommentArchiveRequest::close)
        ProfileManager.setActiveProfile(context, originalProfile)
        profiles.forEach { ProfileManager.deleteProfile(context, it) }
        preferences.edit().apply {
            originalArchive?.let { putBoolean(archiveKey, it) } ?: remove(archiveKey)
            originalIncognito?.let { putBoolean(IncognitoMode.KEY, it) } ?: remove(IncognitoMode.KEY)
        }.commit()
    }

    @Test
    fun optedInPagesAndRepliesShareThePostArchiveForTheInitiatingProfile() = onMain {
        val request = capture()
        request.record(0, POST_URL, listOf(comment("parent")), null)
        request.record(0, POST_URL, listOf(comment("reply")), "parent")
        request.close()
        val archived = CommentArchive.read(context, 0, POST_URL)
        assertEquals(listOf("parent", "reply"), archived.map { it.id })
        assertEquals(listOf(null, "parent"), archived.map { it.parentId })

        createProfile()
        assertTrue(CommentArchive.read(context, 0, POST_URL).isEmpty())
    }

    @Test
    fun privacyChangesNeverMakeAnOldRequestEligibleToArchive() = onMain {
        preferences.edit().putBoolean(archiveKey, false).commit()
        val beforeOptIn = capture()
        preferences.edit().putBoolean(archiveKey, true).commit()
        beforeOptIn.record(0, POST_URL, listOf(comment("before-opt-in")), null)

        IncognitoMode.setEnabled(context, true)
        val duringIncognito = capture()
        IncognitoMode.setEnabled(context, false)
        duringIncognito.record(0, POST_URL, listOf(comment("incognito")), null)

        val beforeIncognito = capture()
        IncognitoMode.setEnabled(context, true)
        IncognitoMode.setEnabled(context, false)
        beforeIncognito.record(0, POST_URL, listOf(comment("before-incognito")), null)

        val beforeOptOut = capture()
        preferences.edit().putBoolean(archiveKey, false).commit()
        preferences.edit().putBoolean(archiveKey, true).commit()
        beforeOptOut.record(0, POST_URL, listOf(comment("before-opt-out")), null)
        assertTrue(CommentArchive.read(context, 0, POST_URL).isEmpty())
    }

    @Test
    fun switchingProfilesAndSwitchingBackDiscardsTheInFlightPage() = onMain {
        val initiatingProfile = ProfileManager.getActiveProfileId(context)
        val request = capture()
        createProfile()
        request.record(0, POST_URL, listOf(comment("other-profile")), null)
        CommentArchive.recordForProfile(
            context,
            initiatingProfile,
            0,
            POST_URL,
            listOf(comment("wrong-destination")),
            null
        )
        assertTrue(CommentArchive.read(context, 0, POST_URL).isEmpty())
        assertTrue(ProfileManager.setActiveProfile(context, initiatingProfile))
        request.record(0, POST_URL, listOf(comment("switched-back")), null)
        assertTrue(CommentArchive.read(context, 0, POST_URL).isEmpty())
    }

    @Test
    fun disposedRequestsCannotArchiveLateResults() = onMain {
        val request = capture()
        request.close()
        request.record(0, POST_URL, listOf(comment("late")), null)
        assertTrue(CommentArchive.read(context, 0, POST_URL).isEmpty())
    }

    private fun capture(): CommentArchiveRequest = CommentArchiveRequest.capture(context).also(requests::add)

    private fun createProfile() {
        val profile = requireNotNull(ProfileManager.createProfile(context, "Comment test ${UUID.randomUUID()}"))
        profiles.add(profile.id)
    }

    private fun comment(id: String) = CommentsInfoItem(0, POST_URL, id).apply {
        commentId = id
        commentText = "Viewed post comment $id"
        uploaderName = "Comment tester"
        textualUploadDate = "today"
    }

    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }

    companion object {
        private const val POST_URL = "https://www.youtube.com/post/UgkxArchiveRequest1491"
    }
}
