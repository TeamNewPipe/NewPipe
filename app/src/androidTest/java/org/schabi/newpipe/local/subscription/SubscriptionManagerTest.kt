package org.schabi.newpipe.local.subscription

import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule

class SubscriptionManagerTest {
    private lateinit var database: AppDatabase
    private lateinit var manager: SubscriptionManager

    @get:Rule
    val trampolineScheduler = TrampolineSchedulerRule()

    private fun getAssertOneSubscriptionEntity(): SubscriptionEntity {
        val entities = manager
            .getSubscriptions(FeedGroupEntity.GROUP_ALL_ID, "", false)
            .blockingFirst()
        assertEquals(1, entities.size)
        return entities[0]
    }

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = SubscriptionManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    @Throws(ExtractionException::class, IOException::class)
    fun testInsert() {
        val info = ChannelInfo.getInfo("https://www.youtube.com/c/3blue1brown")
        val subscription = SubscriptionEntity.from(info)

        manager.insertSubscription(subscription)
        val readSubscription = getAssertOneSubscriptionEntity()

        // the uid has changed, since the uid is chosen upon inserting, but the rest should match
        assertEquals(subscription.serviceId, readSubscription.serviceId)
        assertEquals(subscription.url, readSubscription.url)
        assertEquals(subscription.name, readSubscription.name)
        assertEquals(subscription.avatarUrl, readSubscription.avatarUrl)
        assertEquals(subscription.subscriberCount, readSubscription.subscriberCount)
        assertEquals(subscription.description, readSubscription.description)
    }

    @Test
    @Throws(ExtractionException::class, IOException::class)
    fun testUpdateNotificationMode() {
        val info = ChannelInfo.getInfo("https://www.youtube.com/c/veritasium")
        val subscription = SubscriptionEntity.from(info)
        subscription.notificationMode = 0

        manager.insertSubscription(subscription)
        manager.updateNotificationMode(subscription.serviceId, subscription.url, 1)
            .blockingAwait()
        val anotherSubscription = getAssertOneSubscriptionEntity()

        assertEquals(0, subscription.notificationMode)
        assertEquals(subscription.url, anotherSubscription.url)
        assertEquals(1, anotherSubscription.notificationMode)
    }
}
