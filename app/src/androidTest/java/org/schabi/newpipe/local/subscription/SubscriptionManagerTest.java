package org.schabi.newpipe.local.subscription;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.testUtil.TestDatabase;
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule;

import java.io.IOException;
import java.util.List;

public class SubscriptionManagerTest {
    private AppDatabase database;
    private SubscriptionManager manager;

    @Rule
    public TrampolineSchedulerRule trampolineScheduler = new TrampolineSchedulerRule();


    private SubscriptionEntity getAssertOneSubscriptionEntity() {
        final List<SubscriptionEntity> entities = manager
                .getSubscriptions(FeedGroupEntity.GROUP_ALL_ID, "", false)
                .blockingFirst();
        assertEquals(1, entities.size());
        return entities.get(0);
    }


    @Before
    public void setup() {
        database = TestDatabase.Companion.createReplacingNewPipeDatabase();
        manager = new SubscriptionManager(ApplicationProvider.getApplicationContext());
    }

    @After
    public void cleanUp() {
        database.close();
    }

    @Test
    public void testInsert() throws ExtractionException, IOException {
        final ChannelInfo info = ChannelInfo.getInfo("https://www.youtube.com/c/3blue1brown");
        final SubscriptionEntity subscription = SubscriptionEntity.from(info);

        manager.insertSubscription(subscription);
        final SubscriptionEntity readSubscription = getAssertOneSubscriptionEntity();

        // the uid has changed, since the uid is chosen upon inserting, but the rest should match
        assertEquals(subscription.getServiceId(), readSubscription.getServiceId());
        assertEquals(subscription.getUrl(), readSubscription.getUrl());
        assertEquals(subscription.getName(), readSubscription.getName());
        assertEquals(subscription.getAvatarUrl(), readSubscription.getAvatarUrl());
        assertEquals(subscription.getSubscriberCount(), readSubscription.getSubscriberCount());
        assertEquals(subscription.getDescription(), readSubscription.getDescription());
    }

    @Test
    public void testUpdateNotificationMode() throws ExtractionException, IOException {
        final ChannelInfo info = ChannelInfo.getInfo("https://www.youtube.com/c/veritasium");
        final SubscriptionEntity subscription = SubscriptionEntity.from(info);
        subscription.setNotificationMode(0);

        manager.insertSubscription(subscription);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 1)
                .blockingAwait();
        final SubscriptionEntity anotherSubscription = getAssertOneSubscriptionEntity();

        assertEquals(0, subscription.getNotificationMode());
        assertEquals(subscription.getUrl(), anotherSubscription.getUrl());
        assertEquals(1, anotherSubscription.getNotificationMode());
    }
}
