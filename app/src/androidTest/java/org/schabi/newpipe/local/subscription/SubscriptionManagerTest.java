package org.schabi.newpipe.local.subscription;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.testUtil.TestDatabase;
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
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

        manager.insertSubscription(subscription, info);
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

        manager.insertSubscription(subscription, info);
        manager.updateNotificationMode(subscription.getServiceId(), subscription.getUrl(), 1)
                .blockingAwait();
        final SubscriptionEntity anotherSubscription = getAssertOneSubscriptionEntity();

        assertEquals(0, subscription.getNotificationMode());
        assertEquals(subscription.getUrl(), anotherSubscription.getUrl());
        assertEquals(1, anotherSubscription.getNotificationMode());
    }

    @Test
    public void testRememberRecentStreams() throws ExtractionException, IOException {
        final ChannelInfo info = ChannelInfo.getInfo("https://www.youtube.com/c/Polyphia");
        final List<StreamInfoItem> relatedItems = List.of(
                new StreamInfoItem(0, "a", "b", StreamType.VIDEO_STREAM),
                new StreamInfoItem(1, "c", "d", StreamType.AUDIO_STREAM),
                new StreamInfoItem(2, "e", "f", StreamType.AUDIO_LIVE_STREAM),
                new StreamInfoItem(3, "g", "h", StreamType.LIVE_STREAM));
        relatedItems.forEach(item -> {
            // these two fields must be non-null for the insert to succeed
            item.setUploaderUrl(info.getUrl());
            item.setUploaderName(info.getName());
            // the upload date must not be too much in the past for the item to actually be inserted
            item.setUploadDate(new DateWrapper(OffsetDateTime.now()));
        });
        info.setRelatedItems(relatedItems);
        final SubscriptionEntity subscription = SubscriptionEntity.from(info);

        manager.insertSubscription(subscription, info);
        final List<StreamEntity> streams = database.streamDAO().getAll().blockingFirst();

        assertEquals(4, streams.size());
        streams.sort(Comparator.comparing(StreamEntity::getServiceId));
        for (int i = 0; i < 4; i++) {
            assertEquals(relatedItems.get(0).getServiceId(), streams.get(0).getServiceId());
            assertEquals(relatedItems.get(0).getUrl(), streams.get(0).getUrl());
            assertEquals(relatedItems.get(0).getName(), streams.get(0).getTitle());
            assertEquals(relatedItems.get(0).getStreamType(), streams.get(0).getStreamType());
        }
    }
}
