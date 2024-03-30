package org.schabi.newpipe.local.subscription

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity.Companion.from
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.testUtil.TestDatabase.Companion.createReplacingNewPipeDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule
import java.io.IOException

class SubscriptionManagerTest {
    private var database: AppDatabase? = null
    private var manager: SubscriptionManager? = null

    @Rule
    var trampolineScheduler = TrampolineSchedulerRule()
    private val assertOneSubscriptionEntity: SubscriptionEntity
        private get() {
            val entities = manager
                    .getSubscriptions(FeedGroupEntity.GROUP_ALL_ID, "", false)
                    .blockingFirst()
            Assert.assertEquals(1, entities.size.toLong())
            return entities[0]
        }

    @Before
    fun setup() {
        database = createReplacingNewPipeDatabase()
        manager = SubscriptionManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun cleanUp() {
        database!!.close()
    }

    @Test
    @Throws(ExtractionException::class, IOException::class)
    fun testInsert() {
        val info = ChannelInfo.getInfo("https://www.youtube.com/c/3blue1brown")
        val subscription = from(info)
        manager!!.insertSubscription(subscription)
        val readSubscription = assertOneSubscriptionEntity

        // the uid has changed, since the uid is chosen upon inserting, but the rest should match
        Assert.assertEquals(subscription.getServiceId().toLong(), readSubscription.getServiceId().toLong())
        Assert.assertEquals(subscription.getUrl(), readSubscription.getUrl())
        Assert.assertEquals(subscription.getName(), readSubscription.getName())
        Assert.assertEquals(subscription.getAvatarUrl(), readSubscription.getAvatarUrl())
        Assert.assertEquals(subscription.getSubscriberCount(), readSubscription.getSubscriberCount())
        Assert.assertEquals(subscription.getDescription(), readSubscription.getDescription())
    }

    @Test
    @Throws(ExtractionException::class, IOException::class)
    fun testUpdateNotificationMode() {
        val info = ChannelInfo.getInfo("https://www.youtube.com/c/veritasium")
        val subscription = from(info)
        subscription.setNotificationMode(0)
        manager!!.insertSubscription(subscription)
        manager!!.updateNotificationMode(subscription.getServiceId(), subscription.getUrl()!!, 1)
                .blockingAwait()
        val anotherSubscription = assertOneSubscriptionEntity
        Assert.assertEquals(0, subscription.getNotificationMode().toLong())
        Assert.assertEquals(subscription.getUrl(), anotherSubscription.getUrl())
        Assert.assertEquals(1, anotherSubscription.getNotificationMode().toLong())
    }
}
