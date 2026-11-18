package org.schabi.newpipe.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamType

class FeedDAOTest {
    private lateinit var db: AppDatabase
    private lateinit var feedDAO: FeedDAO
    private lateinit var streamDAO: StreamDAO
    private lateinit var subscriptionDAO: SubscriptionDAO

    private val serviceId = ServiceList.YouTube.serviceId

    private val stream1 = StreamEntity(1, serviceId, "https://youtube.com/watch?v=1", "stream 1", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-01", OffsetDateTime.parse("2023-01-01T00:00:00Z"))
    private val stream2 = StreamEntity(2, serviceId, "https://youtube.com/watch?v=2", "stream 2", StreamType.VIDEO_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-02", OffsetDateTime.parse("2023-01-02T00:00:00Z"))
    private val stream3 = StreamEntity(3, serviceId, "https://youtube.com/watch?v=3", "stream 3", StreamType.LIVE_STREAM, 1000, "channel-1", "https://youtube.com/channel/1", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-01-03", OffsetDateTime.parse("2023-01-03T00:00:00Z"))
    private val stream4 = StreamEntity(4, serviceId, "https://youtube.com/watch?v=4", "stream 4", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))
    private val stream5 = StreamEntity(5, serviceId, "https://youtube.com/watch?v=5", "stream 5", StreamType.VIDEO_STREAM, 1000, "channel-2", "https://youtube.com/channel/2", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-20", OffsetDateTime.parse("2023-08-20T00:00:00Z"))
    private val stream6 = StreamEntity(6, serviceId, "https://youtube.com/watch?v=6", "stream 6", StreamType.VIDEO_STREAM, 1000, "channel-3", "https://youtube.com/channel/3", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-09-01", OffsetDateTime.parse("2023-09-01T00:00:00Z"))
    private val stream7 = StreamEntity(7, serviceId, "https://youtube.com/watch?v=7", "stream 7", StreamType.VIDEO_STREAM, 1000, "channel-4", "https://youtube.com/channel/4", "https://i.ytimg.com/vi/1/hqdefault.jpg", 100, "2023-08-10", OffsetDateTime.parse("2023-08-10T00:00:00Z"))

    private val allStreams = listOf(
        stream1,
        stream2,
        stream3,
        stream4,
        stream5,
        stream6,
        stream7
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
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
    fun allPlatformFeedsRefreshAndCountOnlySubscriptionsInTheRequestedProfileAndGroup() {
        clearAndFillTables()
        val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
        val extra = SubscriptionEntity.from(channelInfo("extra", "https://example.com/channel", "Other platform"))
            .apply {
                uid = 10
                serviceId = 1
            }
        val otherProfile = SubscriptionEntity.from(channelInfo("private", "https://example.com/private", "Private"))
            .apply {
                uid = 11
                serviceId = 1
                profileId = "11111111-1111-1111-1111-111111111111"
            }
        subscriptionDAO.insertAll(listOf(extra, otherProfile))
        streamDAO.insertAll(listOf(stream1.copy(uid = 10, serviceId = 1, url = "https://example.com/video"), stream1.copy(uid = 11, serviceId = 1, url = "https://example.com/private-video")))
        feedDAO.insertAll(listOf(FeedEntity(10, 10), FeedEntity(11, 11)))
        val all = -1
        val modes = SubscriptionEntity.YOUTUBE_MODE_REGULAR or SubscriptionEntity.YOUTUBE_MODE_MUSIC
        fun ids(service: Int, group: Long = FeedGroupEntity.GROUP_ALL_ID) = feedDAO.getStreams(
            profile,
            group,
            true,
            true,
            null,
            service,
            modes
        ).blockingGet()!!.map { it.stream.uid }.toSet()
        assertEquals((1L..7L).toSet(), ids(serviceId))
        assertEquals((1L..7L).toSet() + 10L, ids(all))
        assertEquals(5L, feedDAO.notLoadedCount(profile, all, modes).blockingFirst())
        val now = OffsetDateTime.parse("2026-10-04T12:00:00Z")
        assertEquals(setOf(1L, 2L, 3L, 4L, 10L), feedDAO.getAllOutdatedForScope(profile, all, modes, now).blockingFirst().map { it.uid }.toSet())
        feedDAO.setLastUpdatedForSubscription(FeedLastUpdatedEntity(10, 1, now))
        assertEquals(4L, feedDAO.notLoadedCount(profile, all, modes).blockingFirst())
        val group = db.feedGroupDAO().insert(
            FeedGroupEntity(0, "Mixed", org.schabi.newpipe.local.subscription.FeedGroupIcon.WHATS_NEW)
        )
        db.feedGroupDAO().updateSubscriptionsForGroupForProfile(profile, group, listOf(1L, 10L))
        assertEquals(setOf(1L, 2L, 3L, 10L), ids(all, group))
        assertEquals(1L, feedDAO.notLoadedCountForGroup(profile, group, all, modes).blockingFirst())
        assertEquals(listOf(now), feedDAO.oldestSubscriptionUpdate(profile, group, all, modes).blockingFirst())
        assertEquals(setOf(1L), feedDAO.getAllOutdatedForGroupAndScope(profile, group, all, modes, now.minusSeconds(1)).blockingFirst().map { it.uid }.toSet())
    }

    @Test
    fun thresholdSkipsRecentSubscriptionsButImmediateRefreshIncludesThem() {
        clearAndFillTables()
        val now = OffsetDateTime.parse("2026-10-04T12:00:00Z")
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(1, SubscriptionEntity.YOUTUBE_MODE_REGULAR, now.minusSeconds(60))
        )
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(2, SubscriptionEntity.YOUTUBE_MODE_REGULAR, now.minusSeconds(600))
        )
        fun eligible(cutoff: OffsetDateTime) = feedDAO.getAllOutdatedForScope(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            serviceId,
            SubscriptionEntity.YOUTUBE_MODE_REGULAR,
            cutoff
        ).blockingFirst().map { it.uid }.toSet()

        // Never-loaded subscriptions remain eligible even while another channel is fresh.
        assertEquals(setOf(2L, 3L, 4L), eligible(now.minusSeconds(300)))
        assertEquals(setOf(1L, 2L, 3L, 4L), eligible(now))
        assertEquals(setOf(1L, 2L, 3L, 4L), eligible(now.plusSeconds(301).minusSeconds(300)))
    }

    @Test
    fun discoveryAndPublicationSortIndependentlyAndRefreshKeepsFirstDiscovery() {
        clearAndFillTables()
        feedDAO.deleteAll()
        feedDAO.insertAll(listOf(FeedEntity(1, 1, firstDiscoveredAt = 200), FeedEntity(2, 1, firstDiscoveredAt = 100)))
        fun ids(discovery: Boolean) = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            true,
            true,
            null,
            serviceId,
            SubscriptionEntity.YOUTUBE_MODE_REGULAR,
            discovery
        ).blockingGet()!!.map { it.stream.uid }
        assertEquals(listOf(2L, 1L), ids(false))
        assertEquals(listOf(1L, 2L), ids(true))
        feedDAO.insert(FeedEntity(2, 1, firstDiscoveredAt = 300))
        assertEquals(listOf(1L, 2L), ids(true))
        assertEquals(100L, feedDAO.getMemberships(1).single { it.streamId == 2L }.firstDiscoveredAt)
    }

    @Test
    fun streamUpsertKeepsKnownShortFormClassification() {
        val identifiedShort = stream1.copy(
            uid = 0,
            url = "https://youtube.com/watch?v=short-metadata",
            duration = 60,
            isShortFormContent = true
        )
        streamDAO.upsert(identifiedShort)

        streamDAO.upsert(
            identifiedShort.copy(
                uid = 0,
                isShortFormContent = false
            )
        )

        assertTrue(
            streamDAO.getStreamDirect(serviceId, identifiedShort.url)!!.isShortFormContent
        )
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepOne() {
        setupUnlinkDelete("2023-08-15T00:00:00Z")
        val streams = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun testUnlinkStreamsOlderThan_KeepMultiple() {
        setupUnlinkDelete("2023-08-01T00:00:00Z")
        val streams = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        )
            .blockingGet()
        val allowedStreams = listOf(stream3, stream4, stream5, stream6, stream7)
        assertEqual(streams, allowedStreams)
    }

    @Test
    fun youtubeMusicFeedOnlyIncludesMusicMemberships() {
        clearAndFillTables()
        val musicChannel = subscriptionDAO.getSubscriptionDirect(
            serviceId,
            "https://youtube.com/channel/1"
        )!!
        musicChannel.youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        subscriptionDAO.update(musicChannel)
        feedDAO.deleteAll()
        feedDAO.insertAll(
            listOf(
                FeedEntity(1, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC),
                FeedEntity(2, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC),
                FeedEntity(3, 1, SubscriptionEntity.YOUTUBE_MODE_MUSIC)
            )
        )

        val streams = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
        ).blockingGet()

        assertEqual(streams, listOf(stream1, stream2, stream3))
    }

    @Test
    fun youtubeAndYoutubeMusicHaveIndependentRefreshTimes() {
        clearAndFillTables()
        val sharedChannel = subscriptionDAO.getSubscriptionDirect(
            serviceId,
            "https://youtube.com/channel/1"
        )!!
        sharedChannel.youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_ALL
        subscriptionDAO.update(sharedChannel)
        val regularUpdate = OffsetDateTime.parse("2023-09-01T00:00:00Z")
        val musicUpdate = OffsetDateTime.parse("2023-09-02T00:00:00Z")
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(
                sharedChannel.uid,
                SubscriptionEntity.YOUTUBE_MODE_REGULAR,
                regularUpdate
            )
        )
        feedDAO.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(
                sharedChannel.uid,
                SubscriptionEntity.YOUTUBE_MODE_MUSIC,
                musicUpdate
            )
        )

        assertEquals(
            regularUpdate,
            feedDAO.oldestSubscriptionUpdateFromAll(
                SubscriptionEntity.DEFAULT_PROFILE_ID,
                serviceId,
                SubscriptionEntity.YOUTUBE_MODE_REGULAR
            ).blockingFirst().first()
        )
        assertEquals(
            musicUpdate,
            feedDAO.oldestSubscriptionUpdateFromAll(
                SubscriptionEntity.DEFAULT_PROFILE_ID,
                serviceId,
                SubscriptionEntity.YOUTUBE_MODE_MUSIC
            ).blockingFirst().first()
        )
    }

    @Test
    fun feedOnlyIncludesSelectedService() {
        db.clearAllTables()
        val otherServiceId = ServiceList.SoundCloud.serviceId
        val youtubeSubscription = SubscriptionEntity(
            uid = 1,
            serviceId = serviceId,
            url = "https://youtube.com/channel/1",
            name = "YouTube channel"
        )
        val soundCloudSubscription = SubscriptionEntity(
            uid = 2,
            serviceId = otherServiceId,
            url = "https://soundcloud.com/channel-2",
            name = "SoundCloud channel"
        )
        val soundCloudStream = StreamEntity(
            8,
            otherServiceId,
            "https://soundcloud.com/channel-2/stream",
            "SoundCloud stream",
            StreamType.AUDIO_STREAM,
            1000,
            "SoundCloud channel",
            "https://soundcloud.com/channel-2",
            "",
            100,
            "2023-09-02",
            OffsetDateTime.parse("2023-09-02T00:00:00Z")
        )
        subscriptionDAO.insertAll(listOf(youtubeSubscription, soundCloudSubscription))
        streamDAO.insertAll(listOf(stream1, soundCloudStream))
        feedDAO.insertAll(listOf(FeedEntity(1, 1), FeedEntity(8, 2)))

        val youtubeStreams = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = serviceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        ).blockingGet()
        val soundCloudStreams = feedDAO.getStreams(
            SubscriptionEntity.DEFAULT_PROFILE_ID,
            FeedGroupEntity.GROUP_ALL_ID,
            includePlayed = true,
            includePartiallyPlayed = true,
            uploadDateBefore = null,
            serviceId = otherServiceId,
            youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR
        ).blockingGet()

        assertEqual(youtubeStreams, listOf(stream1))
        assertEqual(soundCloudStreams, listOf(soundCloudStream))
    }

    @Test
    fun cachedUndatedShortsSortAfterPublishedVideosButRemainDiscoverable() {
        clearAndFillTables()
        feedDAO.deleteAll()
        val fallback = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val unknown = stream1.copy(
            uid = 10,
            url = "https://youtube.com/shorts/unknown",
            textualUploadDate = null,
            uploadDate = fallback,
            isUploadDateApproximation = true
        )
        val olderUnknown = unknown.copy(uid = 11, url = "https://youtube.com/watch?v=legacy", uploadDate = fallback.minusSeconds(1))
        val relative = stream2.copy(
            uid = 12,
            url = "https://youtube.com/shorts/relative",
            textualUploadDate = "2 days ago",
            uploadDate = fallback.minusDays(2),
            isUploadDateApproximation = true
        )
        // Exact source dates without text must not be confused with a fallback.
        val exact = stream2.copy(
            uid = 13,
            url = "https://youtube.com/shorts/exact",
            textualUploadDate = null,
            uploadDate = fallback.minusDays(1),
            isUploadDateApproximation = false
        )
        val live = stream3.copy(uid = 14, url = "https://youtube.com/watch?v=live", uploadDate = null)
        streamDAO.insertAll(listOf(unknown, olderUnknown, relative, exact, live))
        feedDAO.insertAll(
            listOf(
                FeedEntity(10, 1, firstDiscoveredAt = 500),
                FeedEntity(11, 1, firstDiscoveredAt = 400),
                FeedEntity(12, 1, firstDiscoveredAt = 300),
                FeedEntity(13, 1, firstDiscoveredAt = 200),
                FeedEntity(14, 1, firstDiscoveredAt = 100)
            )
        )
        assertEquals(listOf(14L, 13L, 12L, 10L, 11L), feedIds())
        assertEquals(listOf(10L, 11L, 12L, 13L, 14L), feedIds(sortByDiscovery = true))

        // A subsequent refresh neither promotes existing Shorts nor resets discovery time.
        streamDAO.upsert(unknown.copy(uid = 0, uploadDate = fallback.plusDays(1)))
        feedDAO.insert(FeedEntity(10, 1, firstDiscoveredAt = 600))
        assertEquals(fallback, streamDAO.getStreamDirect(10)!!.uploadDate)
        assertEquals(500L, feedDAO.getMemberships(1).single { it.streamId == 10L }.firstDiscoveredAt)
        assertEquals(listOf(14L, 13L, 12L, 10L, 11L), feedIds())
    }

    @Test
    fun sourceRelativeDateReplacesSyntheticDateAndSurvivesLaterUndatedRefresh() {
        clearAndFillTables()
        feedDAO.deleteAll()
        val fallback = stream1.copy(
            uid = 10,
            url = "https://youtube.com/shorts/fallback",
            textualUploadDate = null,
            uploadDate = OffsetDateTime.parse("2026-09-30T12:00:00Z"),
            isUploadDateApproximation = true
        )
        streamDAO.insert(fallback)
        feedDAO.insertAll(listOf(FeedEntity(10, 1), FeedEntity(2, 1)))
        assertTrue(streamDAO.getStreamDirect(10)!!.hasSyntheticUploadDate)
        assertEquals(listOf(2L, 10L), feedIds())

        val sourceDate = fallback.uploadDate!!.minusDays(30)
        streamDAO.upsert(fallback.copy(uid = 0, uploadDate = sourceDate, textualUploadDate = "1 month ago"))
        val corrected = streamDAO.getStreamDirect(10)!!
        assertFalse(corrected.hasSyntheticUploadDate)
        assertEquals(sourceDate, corrected.uploadDate)
        assertEquals("1 month ago", corrected.textualUploadDate)
        assertEquals(listOf(10L, 2L), feedIds())

        streamDAO.upsert(fallback.copy(uid = 0, uploadDate = fallback.uploadDate!!.plusDays(1)))
        assertEquals(sourceDate, streamDAO.getStreamDirect(10)!!.uploadDate)
        assertEquals("1 month ago", streamDAO.getStreamDirect(10)!!.textualUploadDate)

        val exactDate = sourceDate.minusHours(1)
        streamDAO.upsert(fallback.copy(uid = 0, uploadDate = exactDate, isUploadDateApproximation = false))
        streamDAO.upsert(fallback.copy(uid = 0, uploadDate = sourceDate, textualUploadDate = "1 month ago"))
        assertEquals(exactDate, streamDAO.getStreamDirect(10)!!.uploadDate)
        assertFalse(streamDAO.getStreamDirect(10)!!.hasSyntheticUploadDate)
    }

    @Test
    fun publicationLimitPrioritizesSourceDatesWhileDiscoveryKeepsUndatedShorts() {
        clearAndFillTables()
        feedDAO.deleteAll()
        val recent = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val streams = (10L..510L).map { id ->
            stream1.copy(
                uid = id,
                url = "https://youtube.com/watch?v=$id",
                uploadDate = recent.minusSeconds(id),
                isUploadDateApproximation = false
            )
        }
        streamDAO.insertAll(streams)
        val undated = stream1.copy(
            uid = 600,
            url = "https://youtube.com/shorts/undated",
            uploadDate = recent,
            textualUploadDate = null,
            isUploadDateApproximation = true
        )
        streamDAO.insert(undated)
        feedDAO.insertAll((streams + undated).map { FeedEntity(it.uid, 1, firstDiscoveredAt = it.uid) })

        val ids = feedIds()
        assertEquals(500, ids.size)
        assertEquals(10L, ids.first())
        assertEquals(509L, ids.last())
        assertFalse(ids.contains(600L))
        val discovered = feedIds(sortByDiscovery = true)
        assertEquals(500, discovered.size)
        assertEquals(600L, discovered.first())
        val nextPage = feedIds(limit = 100, offset = 500)
        assertEquals(listOf(510L, 600L), nextPage)
        assertTrue(ids.toSet().intersect(nextPage.toSet()).isEmpty())
    }

    @Test
    fun syntheticShortsCannotCrowdPublishedVideosOutOfTheFeed() {
        clearAndFillTables()
        feedDAO.deleteAll()
        val recent = OffsetDateTime.parse("2026-09-30T12:00:00Z")
        val shorts = (100L..599L).map { id ->
            stream1.copy(
                uid = id,
                url = "https://youtube.com/shorts/$id",
                uploadDate = recent.minusSeconds(id),
                textualUploadDate = if (id % 2L == 0L) null else " \t\n",
                isUploadDateApproximation = true,
                isShortFormContent = true
            )
        }
        val exact = stream1.copy(uploadDate = recent.minusDays(1), textualUploadDate = null, isUploadDateApproximation = false)
        val relative = stream2.copy(uploadDate = recent.minusDays(2), textualUploadDate = "2 days ago", isUploadDateApproximation = true)
        val live = stream3.copy(uploadDate = null)
        val older = stream4.copy(uploadDate = recent.minusDays(30))
        val published = listOf(exact, relative, live, older)
        streamDAO.update(published)
        streamDAO.insertAll(shorts)
        feedDAO.insertAll((published + shorts).map { FeedEntity(it.uid, 1, firstDiscoveredAt = it.uid) })

        val ids = feedIds()
        assertEquals(500, ids.size)
        assertEquals(listOf(3L, 1L, 2L, 4L), ids.take(4))
        assertEquals((100L..595L).toList(), ids.drop(4))

        // Explicit discovery sorting still selects the newest discoveries, including Shorts.
        assertEquals((599L downTo 100L).toList(), feedIds(sortByDiscovery = true))
    }

    private fun feedIds(
        sortByDiscovery: Boolean = false,
        limit: Int = 500,
        offset: Int = 0
    ) = feedDAO.getStreams(
        SubscriptionEntity.DEFAULT_PROFILE_ID,
        FeedGroupEntity.GROUP_ALL_ID,
        includePlayed = true,
        includePartiallyPlayed = true,
        uploadDateBefore = null,
        serviceId = serviceId,
        youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        sortByDiscovery = sortByDiscovery,
        limit = limit,
        offset = offset
    ).blockingGet()!!.map { it.stream.uid }

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

    private fun setupUnlinkDelete(time: String) {
        clearAndFillTables()
        Single.fromCallable {
            feedDAO.unlinkStreamsOlderThan(OffsetDateTime.parse(time))
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
                SubscriptionEntity.from(channelInfo("1", "https://youtube.com/channel/1", "channel-1")),
                SubscriptionEntity.from(channelInfo("2", "https://youtube.com/channel/2", "channel-2")),
                SubscriptionEntity.from(channelInfo("3", "https://youtube.com/channel/3", "channel-3")),
                SubscriptionEntity.from(channelInfo("4", "https://youtube.com/channel/4", "channel-4"))
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
                FeedEntity(7, 4)
            )
        )
    }

    private fun channelInfo(id: String, url: String, name: String): ChannelInfo {
        val channelInfoConstructor = ChannelInfo::class.java.constructors
            .first { it.parameterTypes.size == 6 || it.parameterTypes.size == 5 }
        val constructorArguments: Array<Any> = if (channelInfoConstructor.parameterTypes.size == 6) {
            arrayOf<Any>(serviceId, id, url, url, name, createListLinkHandler(id, url))
        } else {
            arrayOf<Any>(serviceId, id, url, url, name)
        }
        return channelInfoConstructor.newInstance(*constructorArguments) as ChannelInfo
    }

    private fun createListLinkHandler(id: String, url: String): ListLinkHandler {
        val constructor = ListLinkHandler::class.java.constructors
            .first { it.parameterTypes.size == 5 }
        val contentFilters: List<Any> = emptyList()
        val sortFilter: Any = if (List::class.java.isAssignableFrom(constructor.parameterTypes[4])) {
            emptyList<Any>()
        } else {
            ""
        }
        return constructor.newInstance(url, url, id, contentFilters, sortFilter)
            as ListLinkHandler
    }
}
