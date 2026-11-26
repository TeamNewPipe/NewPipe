/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionGroupMembershipsTest {
    private lateinit var database: AppDatabase
    private val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
    private val groups get() = database.feedGroupDAO()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bulkChangesPreserveDifferentMembershipsAndSubscriptionSettings() {
        val first = channel("First")
        val second = channel("Second")
        val untouched = channel("Untouched")
        val news = group("News")
        val music = group("Music")
        val shared = group("Shared")
        groups.setGroupsForSubscriptionForProfile(profile, first.uid, listOf(news))
        groups.setGroupsForSubscriptionForProfile(profile, second.uid, listOf(music))
        groups.setGroupsForSubscriptionForProfile(profile, untouched.uid, listOf(news, shared))

        assertEquals(2, groups.changeGroupsForSubscriptionsForProfile(profile, listOf(first.uid, second.uid), listOf(shared), emptyList()))
        assertEquals(setOf(news, shared), memberships(first))
        assertEquals(setOf(music, shared), memberships(second))
        assertEquals(0, groups.changeGroupsForSubscriptionsForProfile(profile, listOf(first.uid, second.uid), listOf(shared, shared), emptyList()))
        assertEquals(3, groups.changeGroupsForSubscriptionsForProfile(profile, listOf(first.uid, second.uid), emptyList(), listOf(news, shared)))
        assertTrue(memberships(first).isEmpty())
        assertEquals(setOf(music), memberships(second))
        assertEquals(setOf(news, shared), memberships(untouched))
        listOf(first, second, untouched).forEach {
            assertEquals(it, database.subscriptionDAO().getSubscriptionForProfile(profile, it.uid))
        }
        database.query("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun invalidForeignAndDeletedIdsCannotCrossProfilesOrRemoveUnrelatedMemberships() {
        val local = channel("Local")
        val foreign = channel("Foreign", "other")
        val removedChannel = channel("Deleted")
        val localGroup = group("Local")
        val foreignGroup = group("Foreign", "other")
        val removedGroup = group("Deleted")
        groups.setGroupsForSubscriptionForProfile("other", foreign.uid, listOf(foreignGroup))
        groups.deleteForProfile(profile, removedGroup)
        database.subscriptionDAO().delete(removedChannel)
        val ids = listOf(local.uid, foreign.uid, removedChannel.uid, -1L)
        val targets = listOf(localGroup, foreignGroup, removedGroup, -1L, -2L, -3L)

        assertEquals(1, groups.changeGroupsForSubscriptionsForProfile(profile, ids, targets, emptyList()))
        assertEquals(setOf(localGroup), memberships(local))
        assertEquals(setOf(foreignGroup), memberships(foreign))
        assertEquals(1, groups.changeGroupsForSubscriptionsForProfile(profile, ids, emptyList(), targets))
        assertTrue(memberships(local).isEmpty())
        assertEquals(setOf(foreignGroup), memberships(foreign))
    }

    @Test
    fun anInsertFailureRollsBackRemovalsInTheSameOperation() {
        val channel = channel("Rollback")
        val original = group("Original")
        val failing = group("Failure")
        groups.setGroupsForSubscriptionForProfile(profile, channel.uid, listOf(original))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_membership BEFORE INSERT ON feed_group_subscription_join " +
                "WHEN NEW.group_id = $failing BEGIN SELECT RAISE(ABORT, 'fixture failure'); END"
        )

        val failure = runCatching {
            groups.changeGroupsForSubscriptionsForProfile(profile, listOf(channel.uid), listOf(failing), listOf(original))
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(setOf(original), memberships(channel))
    }

    @Test
    fun largeSelectionsAreAppliedCompletelyAndEmptySelectionsDoNothing() {
        val channels = (1..1205).map { channel("Channel $it") }
        val first = group("First")
        val second = group("Second")
        val ids = channels.map { it.uid }
        assertEquals(2410, groups.changeGroupsForSubscriptionsForProfile(profile, ids, listOf(first, second), emptyList()))
        assertEquals(ids.toSet(), groups.getSubscriptionIdsForDirectForProfile(profile, first).toSet())
        assertEquals(1205, groups.changeGroupsForSubscriptionsForProfile(profile, ids, emptyList(), listOf(first)))
        assertTrue(groups.getSubscriptionIdsForDirectForProfile(profile, first).isEmpty())
        assertEquals(ids.toSet(), groups.getSubscriptionIdsForDirectForProfile(profile, second).toSet())
        assertEquals(0, groups.changeGroupsForSubscriptionsForProfile(profile, emptyList(), emptyList(), listOf(second)))
        assertEquals(0, groups.changeGroupsForSubscriptionsForProfile(profile, ids, emptyList(), emptyList()))
        assertEquals(ids.toSet(), groups.getSubscriptionIdsForDirectForProfile(profile, second).toSet())
    }

    @Test
    fun explicitDeltasPreserveMembershipsAddedAfterThePickerSnapshot() {
        val channel = channel("Concurrent change")
        val original = group("Original")
        val selected = group("Selected")
        val synced = group("Synced while editing")
        groups.setGroupsForSubscriptionForProfile(profile, channel.uid, listOf(original))
        val snapshot = memberships(channel)
        groups.setGroupsForSubscriptionForProfile(profile, channel.uid, listOf(original, synced))

        groups.changeGroupsForSubscriptionsForProfile(profile, listOf(channel.uid), listOf(selected), snapshot.toList())
        assertEquals(setOf(selected, synced), memberships(channel))
    }

    private fun group(name: String, profileId: String = profile): Long = groups.insert(
        FeedGroupEntity(0, name, FeedGroupIcon.ALL, profileId = profileId)
    )

    private fun channel(name: String, profileId: String = profile): SubscriptionEntity = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/$name",
        name = name,
        profileId = profileId,
        youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR or SubscriptionEntity.YOUTUBE_MODE_MUSIC,
        notificationMode = NotificationMode.KEYWORDS_ONLY,
        notificationKeywords = "saved keywords"
    ).apply { uid = database.subscriptionDAO().insert(this) }

    private fun memberships(channel: SubscriptionEntity): Set<Long> = groups.getGroupIdsForSubscriptionDirectForProfile(channel.profileId, channel.uid).toSet()
}
