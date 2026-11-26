package org.schabi.newpipe.profiles

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var source: String
    private lateinit var target: String

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        source = requireNotNull(ProfileManager.createProfile(context, "Source ${UUID.randomUUID().toString().take(8)}")).id
        target = requireNotNull(ProfileManager.createProfile(context, "Target ${UUID.randomUUID().toString().take(8)}")).id
    }

    @After
    fun cleanup() {
        ProfileManager.setDefaultProfile(context, ProfileManager.DEFAULT_PROFILE_ID)
        ProfileManager.deleteProfile(context, source)
        ProfileManager.deleteProfile(context, target)
        database.close()
    }

    @Test
    fun mergeIsRepeatableAndReplacementPreservesSourceAndPlaylistLinks() {
        val first = StreamEntity(serviceId = 0, url = "https://example.com/a", title = "A", streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel")
        val second = StreamEntity(serviceId = 0, url = "https://example.com/b", title = "B", streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel")
        LocalPlaylistManager(database, source).createPlaylist("Shared", listOf(first)).blockingGet()
        LocalPlaylistManager(database, target).createPlaylist("Shared", listOf(second)).blockingGet()
        database.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Source", profileId = source, feedMuted = true))
        database.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = "https://example.com/channel", name = "Keep this", profileId = target))
        repeat(2) { ProfileTransfer.copy(context, source, target, false).blockingGet() }
        assertEquals("Keep this", database.subscriptionDAO().getAllDirectForProfile(target).single().name)
        assertFalse(database.subscriptionDAO().getAllDirectForProfile(target).single().feedMuted)
        assertEquals(1, database.playlistDAO().getAllDirectForProfile(target).size)
        assertEquals(2L, playlistItemCount(target))
        assertEquals(1L, playlistItemCount(source))
        ProfileTransfer.copy(context, source, target, true).blockingGet()
        assertEquals("Source", database.subscriptionDAO().getAllDirectForProfile(target).single().name)
        assertTrue(database.subscriptionDAO().getAllDirectForProfile(target).single().feedMuted)
        assertTrue(database.subscriptionDAO().getAllDirectForProfile(source).single().feedMuted)
        assertEquals(1L, playlistItemCount(target))
        assertEquals(1L, playlistItemCount(source))
    }

    @Test
    fun mergeKeepsExistingHistoryAndReplacementCopiesSourceHistory() {
        val stream = StreamEntity(serviceId = 0, url = "https://example.com/history", title = "History", streamType = StreamType.VIDEO_STREAM, duration = 120, uploader = "Channel")
        val id = database.streamDAO().insert(stream)
        val date = java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z")
        database.streamHistoryDAO().insert(org.schabi.newpipe.database.history.model.StreamHistoryEntity(id, date, 3, source))
        database.streamHistoryDAO().insert(org.schabi.newpipe.database.history.model.StreamHistoryEntity(id, date.plusDays(1), 2, target))
        repeat(2) { ProfileTransfer.copy(context, source, target, false).blockingGet() }
        assertEquals(2L, database.streamHistoryDAO().getLatestEntryForProfile(target, id)!!.repeatCount)
        ProfileTransfer.copy(context, source, target, true).blockingGet()
        assertEquals(3L, database.streamHistoryDAO().getLatestEntryForProfile(target, id)!!.repeatCount)
        assertEquals(3L, database.streamHistoryDAO().getLatestEntryForProfile(source, id)!!.repeatCount)
    }

    @Test
    fun categoriesWithSharedIdentityButDifferentNamesStaySeparate() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sourceKey = org.schabi.newpipe.local.bookmark.PlaylistCategories.preferenceKey(source)
        val targetKey = org.schabi.newpipe.local.bookmark.PlaylistCategories.preferenceKey(target)
        prefs.edit().putString(sourceKey, """{"categories":[{"id":"same","name":"Source category"}],"memberships":{"remote:0:source":"same"}}""")
            .putString(targetKey, """{"categories":[{"id":"same","name":"Destination category"}],"memberships":{"remote:0:destination":"same"}}""").commit()
        try {
            ProfileTransferPreferences.copy(context, source, target, false, emptyMap())
            val result = org.json.JSONObject(prefs.getString(targetKey, null)!!)
            assertEquals(2, result.getJSONArray("categories").length())
            val memberships = result.getJSONObject("memberships")
            assertEquals("same", memberships.getString("remote:0:destination"))
            assertFalse("same" == memberships.getString("remote:0:source"))
        } finally {
            prefs.edit().remove(sourceKey).remove(targetKey).commit()
        }
    }

    @Test
    fun failedReplacementRollsBackDestinationRows() {
        database.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = "https://example.com/keep", name = "Keep", profileId = target))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = org.schabi.newpipe.local.bookmark.PlaylistCategories.preferenceKey(source)
        prefs.edit().putString(key, "malformed").commit()
        try {
            assertTrue(runCatching { ProfileTransfer.copy(context, source, target, true).blockingGet() }.isFailure)
            assertEquals("Keep", database.subscriptionDAO().getAllDirectForProfile(target).single().name)
        } finally {
            prefs.edit().remove(key).commit()
        }
    }

    @Test
    fun originalDefaultCanBeDeletedWithoutReappearingOrBeingResurrectedBySync() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldIds = prefs.getStringSet("wizestream_profile_ids", emptySet())!!.toSet()
        val oldTombstones = prefs.getStringSet("wizestream_profile_sync_tombstones", emptySet())!!.toSet()
        val oldDefault = ProfileManager.getDefaultProfileId(context)
        try {
            assertTrue(ProfileManager.setDefaultProfile(context, target))
            assertFalse(ProfileManager.canDeleteProfile(context, target))
            assertTrue(ProfileManager.deleteProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
            assertNull(ProfileManager.getProfile(context, ProfileManager.DEFAULT_PROFILE_ID))
            assertEquals(
                ProfileSyncMergeResult.TOMBSTONED,
                ProfileManager.upsertSyncedProfile(
                    context,
                    ProfileManager.DEFAULT_PROFILE_ID,
                    "",
                    "",
                    ProfileIcon.PERSON.key,
                    0,
                    1
                )
            )
            prefs.edit().putString(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY, "missing").commit()
            assertEquals(target, ProfileManager.getActiveProfileId(context))
        } finally {
            prefs.edit().putStringSet("wizestream_profile_ids", oldIds)
                .putStringSet("wizestream_profile_sync_tombstones", oldTombstones).commit()
            ProfileManager.setDefaultProfile(context, oldDefault)
            ProfileManager.getProfiles(context)
        }
    }

    private fun playlistItemCount(profile: String): Long = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM playlist_stream_join WHERE playlist_id IN (SELECT uid FROM playlists WHERE profile_id = ?)",
        arrayOf(profile)
    ).use {
        it.moveToFirst()
        it.getLong(0)
    }
}
