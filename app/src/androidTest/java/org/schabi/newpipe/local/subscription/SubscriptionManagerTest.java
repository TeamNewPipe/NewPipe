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
import org.schabi.newpipe.testUtil.TestDatabase;
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule;

import java.util.List;

public class SubscriptionManagerTest {
    private static final int SERVICE_ID = 0;
    private static final String CHANNEL_URL = "https://example.com/channel/test-channel";
    private static final String CHANNEL_NAME = "Test Channel";
    private static final String CHANNEL_AVATAR_URL = "https://example.com/avatar.jpg";
    private static final long CHANNEL_SUBSCRIBER_COUNT = 123456L;
    private static final String CHANNEL_DESCRIPTION = "Deterministic subscription fixture";

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

    private SubscriptionEntity createSubscriptionEntity() {
        final SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setServiceId(SERVICE_ID);
        subscription.setUrl(CHANNEL_URL);
        subscription.setName(CHANNEL_NAME);
        subscription.setAvatarUrl(CHANNEL_AVATAR_URL);
        subscription.setSubscriberCount(CHANNEL_SUBSCRIBER_COUNT);
        subscription.setDescription(CHANNEL_DESCRIPTION);
        return subscription;
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
    public void testInsert() {
        final SubscriptionEntity subscription = createSubscriptionEntity();

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
    public void testUpdateNotificationMode() {
        final SubscriptionEntity subscription = createSubscriptionEntity();
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
