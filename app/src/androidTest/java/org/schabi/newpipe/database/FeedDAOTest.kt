package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.core.Single
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset

class FeedDAOTest {
    private lateinit var db: AppDatabase
    private lateinit var feedDAO: FeedDAO
    private lateinit var streamDAO: StreamDAO
    private lateinit var subscriptionDAO: SubscriptionDAO

    private val serviceId = ServiceList.YouTube.serviceId

    private val stream1 =
        createStreamEntity(
            1, "https://youtube.com/watch?v=1", "stream 1", uploader = "channel-1",
            uploaderUrl = "https://youtube.com/channel/1", date = LocalDate.of(2023, Month.JANUARY, 2),
        )
    private val stream2 =
        createStreamEntity(
            2, "https://youtube.com/watch?v=2", "stream 2", uploader = "channel-1",
            uploaderUrl = "https://youtube.com/channel/1", date = LocalDate.of(2023, Month.JANUARY, 2),
        )
    private val stream3 =
        createStreamEntity(
            3, "https://youtube.com/watch?v=3", "stream 3", StreamType.LIVE_STREAM,
            "channel-1", "https://youtube.com/channel/1", LocalDate.of(2023, Month.JANUARY, 3),
        )
    private val stream4 =
        createStreamEntity(
            4, "https://youtube.com/watch?v=4", "stream 4", uploader = "channel-2",
            uploaderUrl = "https://youtube.com/channel/2", date = LocalDate.of(2023, Month.AUGUST, 10),
        )
    private val stream5 =
        createStreamEntity(
            5, "https://youtube.com/watch?v=5", "stream 5", uploader = "channel-2",
            uploaderUrl = "https://youtube.com/channel/2", date = LocalDate.of(2023, Month.AUGUST, 20),
        )
    private val stream6 =
        createStreamEntity(
            6, "https://youtube.com/watch?v=6", "stream 6", uploader = "channel-3",
            uploaderUrl = "https://youtube.com/channel/3", date = LocalDate.of(2023, Month.SEPTEMBER, 1),
        )
    private val stream7 =
        createStreamEntity(
            7, "https://youtube.com/watch?v=7", "stream 7", uploader = "channel-4",
            uploaderUrl = "https://youtube.com/channel/4", date = LocalDate.of(2023, Month.AUGUST, 10),
        )

    private val allStreams = listOf(stream1, stream2, stream3, stream4, stream5, stream6, stream7)

    private fun createStreamEntity(
        uid: Long,
        url: String,
        title: String,
        type: StreamType = StreamType.VIDEO_STREAM,
        uploader: String,
        uploaderUrl: String,
        date: LocalDate,
    ) = StreamEntity(
        uid, serviceId, url, title, type, duration = 1000, uploader, uploaderUrl,
        thumbnailUrl = "https://i.ytimg.com/vi/1/hqdefault.jpg", viewCount = 100, textualUploadDate = date.toString(),
        uploadInstant = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        feedDAO = db.feedDAO()
        streamDAO = db.streamDAO()
        subscriptionDAO = db.subscriptionDAO()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepOne() {
        setupUnlinkDelete(LocalDate.of(2023, Month.AUGUST, 15))
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID, includePlayed = true, includePartiallyPlayed = true, null
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepMultiple() {
        setupUnlinkDelete(LocalDate.of(2023, Month.AUGUST, 1))
        val streams = feedDAO.getStreams(
            FeedGroupEntity.GROUP_ALL_ID, includePlayed = true, includePartiallyPlayed = true, null
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream4, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    private fun assertEqual(streams: List<StreamWithState>?, allowedStreams: List<StreamEntity>) {
        assertNotNull(streams)
        assertEquals(
            allowedStreams,
            streams!!
                .map { it.stream }
                .sortedBy { it.uid }
                .toList()
        )
    }

    private fun setupUnlinkDelete(localDate: LocalDate) {
        clearAndFillTables()
        Single.fromCallable {
            feedDAO.unlinkStreamsOlderThan(localDate.atStartOfDay(ZoneOffset.UTC).toInstant())
        }.blockingSubscribe()
        Single.fromCallable {
            streamDAO.deleteOrphans()
        }.blockingSubscribe()
    }

    private fun clearAndFillTables() {
        db.clearAllTables()
        streamDAO.insertAll(allStreams)
        subscriptionDAO.insertAll(
            listOf(
                SubscriptionEntity.from(ChannelInfo(serviceId, "1", "https://youtube.com/channel/1", "https://youtube.com/channel/1", "channel-1")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "2", "https://youtube.com/channel/2", "https://youtube.com/channel/2", "channel-2")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "3", "https://youtube.com/channel/3", "https://youtube.com/channel/3", "channel-3")),
                SubscriptionEntity.from(ChannelInfo(serviceId, "4", "https://youtube.com/channel/4", "https://youtube.com/channel/4", "channel-4")),
            )
        )
        feedDAO.insertAll(
            listOf(
                FeedEntity(1, 1),
                FeedEntity(2, 1),
                FeedEntity(3, 1),
                FeedEntity(4, 2),
                FeedEntity(5, 2),
                FeedEntity(6, 3),
                FeedEntity(7, 4),
            )
        )
    }
}
