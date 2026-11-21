/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.feed

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
class FeedSeenStoreProfileTest {
    private lateinit var context: Context
    private val first = FeedSeenLedger.identity(0, "https://example.org/video-a")
    private val second = FeedSeenLedger.identity(0, "https://example.org/video-b")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun persistedMarkersStayIsolatedAcrossProfilesAndRecreation() {
        val a = FeedSeenStore(context, "profile-a")
        val b = FeedSeenStore(context, "profile-b")
        assertTrue(a.update(first, true))
        assertTrue(b.update(second, true))
        a.flush()
        b.flush()

        val restoredA = FeedSeenStore(context, "profile-a")
        val restoredB = FeedSeenStore(context, "profile-b")
        assertTrue(restoredA.isSeen(first))
        assertFalse(restoredA.isSeen(second))
        assertTrue(restoredB.isSeen(second))
        assertFalse(restoredB.isSeen(first))

        FeedSeenStore.forgetProfile(context, "profile-a")
        assertFalse(FeedSeenStore(context, "profile-a").isSeen(first))
        assertTrue(FeedSeenStore(context, "profile-b").isSeen(second))
    }

    @Test
    fun deletingProfileRemovesItsSeenLedgerOnly() {
        val removed = ProfileManager.createProfile(context, "Seen cleanup profile")
        assertNotNull(removed)
        val profileId = checkNotNull(removed).id
        val other = ProfileManager.DEFAULT_PROFILE_ID
        val staleStore = FeedSeenStore(context, profileId).apply {
            update(first, true)
            flush()
        }
        FeedSeenStore(context, other).apply {
            update(second, true)
            flush()
        }
        assertTrue(ProfileManager.deleteProfile(context, profileId))
        // A late onPause/onResume flush must not resurrect this deleted profile's markers.
        staleStore.update(second, true)
        staleStore.flush()
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context)
                .contains("feed_seen_v1_$profileId")
        )
        assertFalse(FeedSeenStore(context, profileId).isSeen(first))
        assertTrue(FeedSeenStore(context, other).isSeen(second))
    }
}
