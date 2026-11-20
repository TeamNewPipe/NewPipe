package org.schabi.newpipe.recommendations

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeContentDisclosure
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.util.ContentBlockingHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalRecommendationsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
    private val profile get() = ProfileManager.getActiveProfileId(context)

    @Before
    fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        prefs.edit().clear().commit()
    }

    private fun item(name: String, service: Int = 0, short: Boolean = false) = StreamInfoItem(service, "https://example.org/$name", name, StreamType.VIDEO_STREAM).apply {
        uploaderName = "Channel"
        isShortFormContent = short
        duration = 120
    }
    private fun enable(vararg settings: Pair<String, Boolean>) = LocalRecommendations.configure(context, mapOf("enabled" to true, *settings), "")
    private fun search(text: String, service: Int = 0) = LocalRecommendations.searched(context, service, text, profile)
    private fun model() = JSONObject(prefs.getString("$profile:model", "{}").orEmpty())

    @Test
    fun disabledByDefaultAndIncognitoNeitherLearnsNorPersonalizes() {
        val items = listOf(item("cooking"), item("astronomy"))
        LocalRecommendations.observe(context, items)
        assertTrue(prefs.all.isEmpty())
        enable()
        search("astronomy")
        LocalRecommendations.observe(context, items)
        assertEquals("astronomy", LocalRecommendations.rank(context, items).first().name)
        IncognitoMode.setEnabled(context, true)
        val before = prefs.all.toMap()
        search("cooking")
        LocalRecommendations.observe(context, listOf(item("private")))
        LocalRecommendations.watched(context, info("private"), 30)
        assertEquals(before, prefs.all)
        assertEquals(items, LocalRecommendations.rank(context, items))
        assertTrue(LocalRecommendations.feed(context, 0).isEmpty())
    }

    @Test
    fun forYouPreservesChannelAvatarsAcrossModelPersistence() {
        enable()
        val avatarUrl = "https://example.org/channel-avatar.jpg"
        val astronomy = item("astronomy").apply { uploaderAvatarUrl = avatarUrl }
        LocalRecommendations.observe(context, listOf(astronomy))

        assertEquals(avatarUrl, LocalRecommendations.feed(context, 0).single().uploaderAvatarUrl)
        val stored = model()
        assertEquals(avatarUrl, stored.getJSONArray("candidates").getJSONObject(0).getString("avatar"))

        // Existing users may have saved recommendations from before this field was added.
        stored.getJSONArray("candidates").getJSONObject(0).remove("avatar")
        prefs.edit().putString("$profile:model", stored.toString()).commit()
        assertEquals(1, LocalRecommendations.feed(context, 0).size)
    }

    @Test
    fun feedSnapshotMatchesFeedAndInterestSummary() {
        enable()
        search("astronomy galaxies")
        LocalRecommendations.observe(context, listOf(item("astronomy"), item("galaxies")))
        val snapshot = LocalRecommendations.feedSnapshot(context, 0)
        assertEquals(LocalRecommendations.feed(context, 0).map { it.url }, snapshot.items.map { it.url })
        assertEquals(LocalRecommendations.tasteSummary(context).interests, snapshot.interests)
    }

    @Test
    fun pauseFreezesLearningAndResetDoesNotDeleteHistoryPreferences() {
        enable()
        search("astronomy")
        LocalRecommendations.observe(context, listOf(item("astronomy")))
        enable("paused" to true)
        val before = model().toString()
        search("cooking")
        LocalRecommendations.observe(context, listOf(item("cooking")))
        assertEquals(before, model().toString())
        assertEquals(1, LocalRecommendations.feed(context, 0).size)
        LocalRecommendations.reset(context)
        assertTrue(LocalRecommendations.feed(context, 0).isEmpty())
        assertTrue(LocalRecommendations.setting(context, "enabled"))
        assertTrue(LocalRecommendations.setting(context, "paused"))
    }

    @Test
    fun profilesAndServicesDoNotShareInterestsOrCandidates() {
        enable()
        val original = profile
        search("astronomy")
        LocalRecommendations.observe(context, listOf(item("astronomy"), item("foreign", 1)))
        assertEquals(listOf("astronomy"), LocalRecommendations.feed(context, 0).map { it.name })
        assertEquals(0.0, LocalRecommendations.score(mapOf("0:astronomy" to 2.0), 1, "astronomy"), 0.0)
        val other = ProfileManager.createProfile(context, "Other")!!
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        assertFalse(LocalRecommendations.enabled(context))
        enable()
        LocalRecommendations.searched(context, 0, "late original profile query", original)
        assertTrue(LocalRecommendations.feed(context, 0).isEmpty())
        assertFalse(prefs.contains("$profile:model"))
        assertTrue(ProfileManager.setActiveProfile(context, original))
        assertEquals(1, LocalRecommendations.feed(context, 0).size)
        assertTrue(ProfileManager.deleteProfile(context, other.id))
        assertFalse(prefs.all.keys.any { it.startsWith("${other.id}:") })
    }

    @Test
    fun excludesBlockedWatchedAndQueuedVideosWithoutAutoplayFallback() {
        enable("autoplay" to true, "shorts" to true)
        val excluded = item("astronomy")
        val blocked = item("blocked")
        val watched = item("watched")
        val next = item("next", short = true)
        val items = listOf(excluded, blocked, watched, next)
        LocalRecommendations.observe(context, items)
        LocalRecommendations.exclude(context, excluded)
        ContentBlockingHelper.blockVideo(context, blocked)
        LocalRecommendations.watched(context, info("watched"), 0)
        assertEquals(listOf(next.url), LocalRecommendations.rank(context, items).map { it.url })
        assertEquals(next, LocalRecommendations.autoplay(context, items, 0, true, emptySet()))
        assertNull(LocalRecommendations.autoplay(context, items, 0, true, setOf(next.url)))
        assertEquals(listOf(next), LocalRecommendations.shorts(context, items))
    }

    @Test
    fun topicExclusionsAndDisabledHistoryAreRespected() {
        enable()
        val defaults = PreferenceManager.getDefaultSharedPreferences(context)
        defaults.edit().putBoolean(context.getString(R.string.enable_search_history_key), false)
            .putBoolean(context.getString(R.string.enable_watch_history_key), false).commit()
        search("astronomy")
        LocalRecommendations.watched(context, info("astronomy"), 60)
        assertFalse(prefs.contains("$profile:model"))
        LocalRecommendations.configure(context, mapOf("enabled" to true), "astronomy")
        assertEquals(listOf("cooking"), LocalRecommendations.rank(context, listOf(item("astronomy"), item("cooking"))).map { it.name })
    }

    @Test
    fun shortFormRankingPenaltiesAreExplicitAndExplained() {
        enable()
        val short = item("short", short = true)
        val brief = item("brief").apply { duration = 299 }
        val long = item("long").apply { duration = 300 }

        assertEquals(0.0, LocalRecommendations.rankingPenalty(context, short), 0.0)
        assertEquals(0.0, LocalRecommendations.rankingPenalty(context, brief), 0.0)

        enable("reduce_shorts" to true, "reduce_short_videos" to true)
        assertEquals(2.0, LocalRecommendations.rankingPenalty(context, short), 0.0)
        assertEquals(1.0, LocalRecommendations.rankingPenalty(context, brief), 0.0)
        assertEquals(0.0, LocalRecommendations.rankingPenalty(context, long), 0.0)
        assertEquals(long, LocalRecommendations.rank(context, listOf(short, brief, long)).first())
        assertTrue(LocalRecommendations.explanation(context, short).contains("Shorts"))
        assertTrue(LocalRecommendations.explanation(context, brief).contains("under 5 minutes"))
    }

    @Test
    fun providerDisclosedAiVideoHasLessPassiveLearningInfluence() {
        enable("reduce_disclosed_ai" to true)
        val disclosure = YoutubeContentDisclosure(
            "Made with AI",
            "Sounds or visuals were altered or generated."
        )
        LocalRecommendations.watched(
            context,
            info("astronomy", metaInfo = listOf(disclosure)),
            30
        )
        val reduced = model().getJSONObject("weights").getDouble("0:astronomy")

        LocalRecommendations.reset(context)
        enable("reduce_disclosed_ai" to false)
        LocalRecommendations.watched(context, info("astronomy"), 30)
        val normal = model().getJSONObject("weights").getDouble("0:astronomy")

        assertEquals(normal * 0.10, reduced, 0.0001)
    }

    @Test
    fun removedInterestCannotBeLearnedAgainFromSearch() {
        enable()
        search("astronomy")
        assertTrue("astronomy" in LocalRecommendations.tasteSummary(context).interests)

        LocalRecommendations.removeInterests(context, profile, setOf("astronomy"))
        search("astronomy geology")

        val interests = LocalRecommendations.tasteSummary(context).interests
        assertFalse("astronomy" in interests)
        assertTrue("geology" in interests)
    }

    @Test
    fun storageIsBoundedAndCorruptModelRecovers() {
        enable()
        repeat(4) { batch -> LocalRecommendations.observe(context, (0 until 100).map { item("video${batch * 100 + it}") }) }
        assertEquals(200, model().getJSONArray("candidates").length())
        repeat(12) { search((0..30).joinToString(" ") { index -> "term${it}word$index" }) }
        assertTrue(model().getJSONObject("weights").length() <= 200)
        prefs.edit().putString("$profile:model", "broken").commit()
        LocalRecommendations.observe(context, listOf(item("recovered")))
        assertEquals("recovered", LocalRecommendations.feed(context, 0).single().name)
    }

    @Test
    fun actualPlayingTimeAddsWeightButPauseAndProfileSwitchDoNot() {
        enable()
        val tracker = RecommendationWatchTracker(context)
        try {
            tracker.start(info("astronomy"))
            assertTrue(RecommendationWorkDispatcher.awaitIdleForTesting())
            val initial = model().getJSONObject("weights").getDouble("0:astronomy")
            tracker.update(true)
            repeat(40) {
                SystemClock.sleep(500)
                tracker.update(true)
            }
            assertTrue(RecommendationWorkDispatcher.awaitIdleForTesting())
            val played = model().getJSONObject("weights").getDouble("0:astronomy")
            assertTrue(played > initial)
            tracker.update(false)
            repeat(10) {
                SystemClock.sleep(1000)
                tracker.update(false)
            }
            assertEquals(played, model().getJSONObject("weights").getDouble("0:astronomy"), 0.0)
            val other = ProfileManager.createProfile(context, "Other")!!
            ProfileManager.setActiveProfile(context, other.id)
            enable()
            repeat(20) {
                SystemClock.sleep(1000)
                tracker.update(true)
            }
            assertFalse(prefs.contains("$profile:model"))
        } finally {
            tracker.close()
        }
    }

    @Test
    fun likesAreBoundedAndChannelAffinityImprovesRelatedRecommendations() {
        enable()
        val preferred = item("another astronomy video").apply {
            uploaderName = "Preferred channel"
            uploaderUrl = "https://example.org/channel/preferred-channel"
        }
        val other = item("another astronomy topic").apply {
            uploaderName = "Other channel"
            uploaderUrl = "https://example.org/channel/other"
        }
        LocalRecommendations.observe(context, listOf(other, preferred))
        LocalRecommendations.watched(
            context,
            info("watched astronomy", "Preferred channel", 50_000, 1_000_000),
            45
        )

        assertEquals(preferred, LocalRecommendations.rank(context, listOf(other, preferred)).first())
        assertTrue(LocalRecommendations.qualityBoost(50_000, 1_000_000) > 0.0)
        assertTrue(LocalRecommendations.qualityBoost(Long.MAX_VALUE, 1) <= 0.60)
        assertEquals(0.0, LocalRecommendations.qualityBoost(-1, 1_000), 0.0)
        assertTrue(LocalRecommendations.explanation(context, preferred).contains("Preferred channel"))
        assertTrue(LocalRecommendations.tasteSummary(context).channels.contains("Preferred channel"))
    }

    @Test
    fun explicitLocalLikesAreStrongAndReversible() {
        enable()
        val other = item("unrelated topic").apply {
            uploaderName = "Other channel"
            uploaderUrl = "https://example.org/channel/other"
        }
        val liked = item("gardening basics").apply {
            uploaderName = "Garden channel"
            uploaderUrl = "https://example.org/channel/garden-channel"
        }
        LocalRecommendations.observe(context, listOf(other, liked))
        val likedInfo = info("gardening basics", "Garden channel")

        LocalRecommendations.liked(context, likedInfo, true)
        assertEquals(liked, LocalRecommendations.rank(context, listOf(other, liked)).first())
        assertTrue(LocalRecommendations.tasteSummary(context).interests.contains("gardening"))

        LocalRecommendations.liked(context, likedInfo, false)
        assertEquals(other, LocalRecommendations.rank(context, listOf(other, liked)).first())
    }

    @Test
    fun syncingHomeTabsPreservesLocalForYouPosition() {
        val local = listOf(Tab.Type.RECOMMENDATIONS.tab, Tab.Type.FEED.tab)
        val synced = listOf(Tab.Type.SUBSCRIPTIONS.tab, Tab.Type.HISTORY.tab)
        assertEquals(listOf(Tab.RecommendationsTab.ID, Tab.SubscriptionsTab.ID, Tab.HistoryTab.ID), RecommendationHomeTabs.preserve(local, synced).map { it.tabId })
        assertEquals(synced, RecommendationHomeTabs.preserve(emptyList(), synced))
    }

    @Test
    fun forYouTabRoundTripsAndOpensTheLocalFeed() {
        val tab = Tab.from(Tab.RecommendationsTab.ID)!!
        assertTrue(tab.getFragment(context) is RecommendationsFragment)
        assertEquals(Tab.RecommendationsTab.ID, tab.tabId)
    }

    private fun info(
        name: String,
        channel: String = "Channel",
        likes: Long = -1,
        views: Long = -1,
        metaInfo: List<MetaInfo> = emptyList()
    ): StreamInfo = mock(StreamInfo::class.java).also {
        `when`(it.serviceId).thenReturn(0)
        `when`(it.url).thenReturn("https://example.org/$name")
        `when`(it.name).thenReturn(name)
        `when`(it.uploaderName).thenReturn(channel)
        `when`(it.uploaderUrl).thenReturn("https://example.org/channel/${channel.lowercase().replace(' ', '-')}")
        `when`(it.likeCount).thenReturn(likes)
        `when`(it.viewCount).thenReturn(views)
        `when`(it.streamType).thenReturn(StreamType.VIDEO_STREAM)
        `when`(it.metaInfo).thenReturn(metaInfo)
    }
}
