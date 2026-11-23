package org.schabi.newpipe.local.subscription

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileSubscriptionIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase(queryExecutor)
    }

    @After
    fun tearDown() {
        try {
            // blockingFirst cancels its Room subscription before returning, but an invalidation
            // query may still be unwinding. Finish queued work before closing its connection pool.
            queryExecutor.submit {}.get(10, SECONDS)
        } finally {
            try {
                if (::database.isInitialized) {
                    NewPipeDatabase.close()
                    // Closing Room cancels its coroutine scope. Drain the queued cleanup before
                    // stopping the executor so cancellation work can still be dispatched.
                    queryExecutor.submit {}.get(10, SECONDS)
                }
            } finally {
                queryExecutor.shutdown()
                assertTrue("Room test executor did not stop", queryExecutor.awaitTermination(10, SECONDS))
            }
        }
    }

    @Test
    fun sameChannelCanExistIndependentlyInTwoProfiles() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val url = "https://example.com/channel/shared"
        val managerA = SubscriptionManager(context, profileA)
        val managerB = SubscriptionManager(context, profileB)

        managerA.insertSubscription(
            SubscriptionEntity(serviceId = 0, url = url, name = "Work channel")
        )
        managerB.insertSubscription(
            SubscriptionEntity(serviceId = 0, url = url, name = "Personal channel")
        )

        val subscriptionsA = managerA.subscriptions().blockingFirst()
        val subscriptionsB = managerB.subscriptions().blockingFirst()
        assertEquals(1, subscriptionsA.size)
        assertEquals(1, subscriptionsB.size)
        assertEquals("Work channel", subscriptionsA.single().name)
        assertEquals("Personal channel", subscriptionsB.single().name)
        assertEquals(profileA, subscriptionsA.single().profileId)
        assertEquals(profileB, subscriptionsB.single().profileId)
        assertEquals(
            1,
            managerA.getSubscriptionFlowable(0, url).blockingFirst().size
        )
        assertEquals(
            1,
            managerB.getSubscriptionFlowable(0, url).blockingFirst().size
        )

        managerA.deleteSubscription(0, url).blockingAwait()
        assertTrue(managerA.subscriptions().blockingFirst().isEmpty())
        assertEquals(1, managerB.subscriptions().blockingFirst().size)
    }

    @Test
    fun copyingGroupIsIdempotentAndPreservesDestinationSettingsAndSource() {
        val subscriptions = database.subscriptionDAO()
        val groups = database.feedGroupDAO()
        val first = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel/shared",
            name = "Source",
            profileId = "source",
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        )
        first.uid = subscriptions.insert(first)
        val second = first.copy(uid = 0, url = "https://example.com/channel/new")
        second.uid = subscriptions.insert(second)
        val existing = first.copy(
            uid = 0,
            profileId = "target",
            name = "Destination name",
            notificationMode = 1,
            notificationKeywords = "keep",
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
        existing.uid = subscriptions.insert(existing)
        val sourceGroup = groups.insert(
            FeedGroupEntity(
                0,
                "Music",
                FeedGroupIcon.MUSIC,
                profileId = "source"
            )
        )
        groups.updateSubscriptionsForGroupForProfile("source", sourceGroup, listOf(first.uid, second.uid))
        val manager = SubscriptionManager(context, "source")
        val targetGroup = manager.copyGroupToProfile(sourceGroup, "target")
        assertEquals(targetGroup, manager.copyGroupToProfile(sourceGroup, "target"))
        assertEquals(1, groups.getAllDirectForProfile("target").size)
        assertEquals(FeedGroupIcon.MUSIC, groups.getGroupDirectForProfile("target", targetGroup)?.icon)
        val copied = subscriptions.getAllForProfile("target").blockingFirst()
        assertEquals(2, copied.size)
        val retained = copied.single { it.uid == existing.uid }
        assertEquals("Destination name", retained.name)
        assertEquals(1, retained.notificationMode)
        assertEquals("keep", retained.notificationKeywords)
        assertEquals(SubscriptionEntity.YOUTUBE_MODE_ALL, retained.youtubeModeMask)
        assertEquals(
            copied.map { it.uid }.toSet(),
            groups.getSubscriptionIdsForDirectForProfile("target", targetGroup).toSet()
        )
        assertEquals(
            setOf(first.uid, second.uid),
            groups.getSubscriptionIdsForDirectForProfile("source", sourceGroup).toSet()
        )
        assertEquals(
            SubscriptionEntity.YOUTUBE_MODE_MUSIC,
            subscriptions.getSubscriptionForProfile("source", first.uid)?.youtubeModeMask
        )
    }

    @Test
    fun copyingGroupRejectsSourceFromAnotherProfileWithoutPartialChanges() {
        val groups = database.feedGroupDAO()
        val foreignGroup = groups.insert(
            FeedGroupEntity(
                0,
                "Private",
                FeedGroupIcon.WORK,
                profileId = "other"
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionManager(context, "source").copyGroupToProfile(foreignGroup, "target")
        }
        assertTrue(groups.getAllDirectForProfile("target").isEmpty())
        assertTrue(database.subscriptionDAO().getAllForProfile("target").blockingFirst().isEmpty())
    }

    @Test
    fun channelGroupsRejectMembershipsFromAnotherProfile() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val subscriptionDao = database.subscriptionDAO()
        val groupDao = database.feedGroupDAO()

        val subscriptionA = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel/a",
            name = "A",
            profileId = profileA
        )
        subscriptionA.uid = subscriptionDao.insert(subscriptionA)
        val subscriptionB = SubscriptionEntity(
            serviceId = 0,
            url = "https://example.com/channel/b",
            name = "B",
            profileId = profileB
        )
        subscriptionB.uid = subscriptionDao.insert(subscriptionB)

        val groupAId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Work",
                icon = FeedGroupIcon.WORK,
                profileId = profileA
            )
        )
        val groupBId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Personal",
                icon = FeedGroupIcon.PERSON,
                profileId = profileB
            )
        )

        groupDao.updateSubscriptionsForGroupForProfile(
            profileA,
            groupAId,
            listOf(subscriptionB.uid)
        )
        assertTrue(
            groupDao.getSubscriptionIdsForDirectForProfile(profileA, groupAId).isEmpty()
        )

        groupDao.updateSubscriptionsForGroupForProfile(
            profileA,
            groupAId,
            listOf(subscriptionA.uid)
        )
        assertEquals(
            listOf(subscriptionA.uid),
            groupDao.getSubscriptionIdsForDirectForProfile(profileA, groupAId)
        )
        assertTrue(
            groupDao.getSubscriptionIdsForDirectForProfile(profileB, groupBId).isEmpty()
        )

        val secondGroupAId = groupDao.insert(
            FeedGroupEntity(
                uid = 0,
                name = "Study",
                icon = FeedGroupIcon.EDUCATION,
                profileId = profileA
            )
        )
        groupDao.setGroupsForSubscriptionForProfile(
            profileA,
            subscriptionA.uid,
            listOf(groupAId, secondGroupAId, groupBId)
        )
        assertEquals(
            listOf(groupAId, secondGroupAId),
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileA,
                subscriptionA.uid
            ).blockingFirst()
        )
        assertTrue(
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileB,
                subscriptionB.uid
            ).blockingFirst().isEmpty()
        )

        groupDao.setGroupsForSubscriptionForProfile(
            profileA,
            subscriptionA.uid,
            emptyList()
        )
        assertTrue(
            groupDao.getGroupIdsForSubscriptionForProfile(
                profileA,
                subscriptionA.uid
            ).blockingFirst().isEmpty()
        )
    }
}
