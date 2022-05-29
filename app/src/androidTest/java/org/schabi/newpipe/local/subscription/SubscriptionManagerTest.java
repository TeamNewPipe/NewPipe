package org.schabi.newpipe.local.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.testUtil.TestDatabase;
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule;

import java.io.IOException;
import java.lang.reflect.Method;

public class SubscriptionManagerTest {
    private AppDatabase database;
    private SubscriptionManager manager;
    private SubscriptionEntity subscription;
    private SubscriptionEntity anotherSubscription;

    @Rule
    public TrampolineSchedulerRule trampolineScheduler = new TrampolineSchedulerRule();

    @Before
    public void setup() throws ExtractionException, IOException {
        database = TestDatabase.Companion.createReplacingNewPipeDatabase();
        manager = new SubscriptionManager(ApplicationProvider.getApplicationContext());

        ChannelInfo info = ChannelInfo.getInfo("https://www.youtube.com/c/3blue1brown");
        subscription = SubscriptionEntity.from(info);
        manager.insertSubscription(subscription, info);
        var entities = manager.getSubscriptions(-1L, subscription.getName(), false).blockingFirst();
        assertEquals(1, entities.size());
        anotherSubscription = entities.get(0);
    }

    @After
    public void cleanUp() {
        database.close();
    }

    @Test
    public void testInsert1() {
        //This test fails because the uid of those two SubscriptionEntity aren't the same.
        assertEquals(subscription, anotherSubscription);
    }

    @Test
    public void testInsert2() {
        assertEquals(subscription.getUid(), anotherSubscription.getUid());
    }

    @Test
    public void testInsert3() {
        assertEquals(subscription.getServiceId(), anotherSubscription.getServiceId());
        assertEquals(subscription.getUrl(), anotherSubscription.getUrl());
        assertEquals(subscription.getName(), anotherSubscription.getName());
        assertEquals(subscription.getAvatarUrl(), anotherSubscription.getAvatarUrl());
        assertEquals(subscription.getSubscriberCount(), anotherSubscription.getSubscriberCount());
        assertEquals(subscription.getDescription(), anotherSubscription.getDescription());
    }


    @Test
    public void testUpdateNotificationMode1() throws ExtractionException, IOException {
        var info = ChannelInfo.getInfo("https://www.youtube.com/c/veritasium");
        var subscription = SubscriptionEntity.from(info);
        subscription.setNotificationMode(0);
        assertEquals(0, subscription.getNotificationMode());
        manager.insertSubscription(subscription, info);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 1);
        var entities = manager.getSubscriptions(-1L, subscription.getName(), false).blockingFirst();
        assertEquals(1, entities.size());
        var anotherSubscription = entities.get(0);

        assertEquals(subscription.getUrl(), anotherSubscription.getUrl());
        assertEquals(1, anotherSubscription.getNotificationMode());
    }

    @Test
    public void testUpdateNotificationMode2() throws ExtractionException, IOException {
        var info = ChannelInfo.getInfo("https://www.youtube.com/c/Radiohead");
        var subscription = SubscriptionEntity.from(info);
        subscription.setNotificationMode(0);
        assertEquals(0, subscription.getNotificationMode());
        manager.insertSubscription(subscription, info);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 1);

        assertEquals(1, subscription.getNotificationMode());
    }

    @Test
    public void testUpdateNotificationMode3() throws ExtractionException, IOException {
        var info = ChannelInfo.getInfo("https://www.youtube.com/c/LinusTechTips");
        var subscription = SubscriptionEntity.from(info);
        subscription.setNotificationMode(1);
        assertEquals(1, subscription.getNotificationMode());
        manager.insertSubscription(subscription, info);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 0);
        var entities = manager.getSubscriptions(-1L, subscription.getName(), false).blockingFirst();
        assertEquals(1, entities.size());
        var anotherSubscription = entities.get(0);

        assertEquals(subscription.getUrl(), anotherSubscription.getUrl());
        assertEquals(0, anotherSubscription.getNotificationMode());
    }

    @Test
    public void testUpdateNotificationMode4() throws ExtractionException, IOException {
        var info = ChannelInfo.getInfo("https://www.youtube.com/c/JetBrainsTV");
        var subscription = SubscriptionEntity.from(info);
        subscription.setNotificationMode(1);
        assertEquals(1, subscription.getNotificationMode());
        manager.insertSubscription(subscription, info);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 0);

        assertEquals(0, subscription.getNotificationMode());
    }

    @Test
    public void testRememberAllStreams() throws ExtractionException, IOException {
        database.streamDAO().deleteAll();
        var info = ChannelInfo.getInfo("https://www.youtube.com/c/Polyphia");
        var subscription = SubscriptionEntity.from(info);
        manager.insertSubscription(subscription, info);

//        var Stream1 = StreamInfo.getInfo("https://www.youtube.com/watch?v=Z5NoQg8LdDk");
//        var Stream2 = StreamInfo.getInfo("https://www.youtube.com/watch?v=2hln1TOQUZ0");
//        var Stream3 = StreamInfo.getInfo("https://www.youtube.com/watch?v=9_gkpYORQLU");
//        var Stream4 = StreamInfo.getInfo("https://www.youtube.com/watch?v=per9Wz0N-QA");

        var streams = database.streamDAO().getAll().blockingFirst();
        assertTrue(streams.size() >= 4);
    }
}
