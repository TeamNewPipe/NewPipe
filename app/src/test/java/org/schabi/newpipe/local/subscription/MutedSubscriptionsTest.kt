package org.schabi.newpipe.local.subscription

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.sync.DeviceIdentity
import org.schabi.newpipe.sync.RoomSubscriptionSyncStore
import org.schabi.newpipe.sync.SubscriptionChange
import org.schabi.newpipe.sync.SubscriptionChangeType
import org.schabi.newpipe.sync.SubscriptionRecordId
import org.schabi.newpipe.sync.SyncedSubscription
import org.schabi.newpipe.util.ServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MutedSubscriptionsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private lateinit var database: AppDatabase
    private lateinit var manager: SubscriptionManager
    private val dao get() = database.subscriptionDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        ServiceHelper.setSelectedServiceId(context, 0)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        manager = SubscriptionManager(context, profile)
    }

    @After
    fun tearDown() {
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun largeBulkMuteIsAtomicProfileScopedAndPreservesEveryOtherSubscriptionField() {
        val channels = database.runInTransaction<List<SubscriptionEntity>> { (1..1005).map { channel("channel-$it") } }
        val foreign = channel("channel-1", profileId = "other")
        val ids = channels.map { it.uid } + foreign.uid + channels.first().uid
        database.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_last_mute BEFORE UPDATE OF feed_muted ON subscriptions " +
                "WHEN NEW.uid = ${channels.last().uid} BEGIN SELECT RAISE(ABORT, 'mute rollback fixture'); END"
        )
        manager.setFeedMuted(ids, true).test().assertError { it.message.orEmpty().contains("mute rollback fixture") }
        assertTrue(dao.getAllDirect().none { it.feedMuted })
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_last_mute")

        manager.setFeedMuted(ids, true).test().assertComplete().assertNoErrors()
        assertEquals(channels.map { it.copy(feedMuted = true) }.sortedBy { it.uid }, dao.getAllDirectForProfile(profile).sortedBy { it.uid })
        assertEquals(foreign, dao.getSubscriptionForProfile("other", foreign.uid))
        manager.setFeedMuted(ids, false).test().assertComplete().assertNoErrors()
        assertEquals(channels.sortedBy { it.uid }, dao.getAllDirectForProfile(profile).sortedBy { it.uid })
    }

    @Test
    fun metadataRefreshImportResubscribeAndNotificationChangesPreserveMute() {
        val original = channel("existing", muted = true)
        val info = channelInfo(original.url!!)
        manager.updateChannelInfo(info).test().assertComplete().assertNoErrors()
        assertTrue(stored(original).feedMuted)
        manager.upsertAll(listOf(info to null))
        assertTrue(stored(original).feedMuted)
        assertEquals(original.notificationKeywords, stored(original).notificationKeywords)
        manager.updateNotificationSettings(0, original.url!!, NotificationMode.ENABLED, "retained").test().assertComplete().assertNoErrors()
        assertTrue(stored(original).feedMuted)
        assertEquals("retained", stored(original).notificationKeywords)
        manager.insertSubscriptionAndReturn(SubscriptionEntity(serviceId = 0, url = original.url, name = "Resubscribed"))
        assertTrue(stored(original).feedMuted)
        assertEquals(3, stored(original).youtubeModeMask)
        val imported = SubscriptionEntity(serviceId = 0, url = "https://example.com/imported", name = "Imported")
        assertEquals(1, manager.insertImportedSubscriptions(listOf(SubscriptionEntity(serviceId = 0, url = original.url), imported)))
        assertTrue(stored(original).feedMuted)
        assertFalse(stored(imported).feedMuted)
    }

    @Test
    fun copyingGroupPreservesDestinationChoicesAndStartsNewDestinationChannelsUnmuted() {
        val first = channel("first", muted = true)
        val second = channel("second", muted = true)
        val destination = channel("first", muted = true, profileId = "destination")
        val groups = database.feedGroupDAO()
        val group = groups.insert(FeedGroupEntity(0, "Channels", FeedGroupIcon.ALL, profileId = profile))
        groups.updateSubscriptionsForGroupForProfile(profile, group, listOf(first.uid, second.uid))
        manager.copyGroupToProfile(group, "destination")
        assertTrue(dao.getSubscriptionForProfile("destination", destination.uid)!!.feedMuted)
        val copied = dao.getSubscriptionDirectForProfile("destination", 0, second.url!!)!!
        assertFalse(copied.feedMuted)
        assertEquals(NotificationMode.DISABLED, copied.notificationMode)
        assertTrue(stored(first).feedMuted)
        assertTrue(stored(second).feedMuted)
    }

    @Test
    fun muteIsAbsentFromWireAndJournalAndSurvivesIncomingLegacySync() {
        val original = channel("sync")
        val store = RoomSubscriptionSyncStore(database, peerId())
        store.reconcileLocalSubscriptions()
        val revisions = store.getKnownRevisions()
        val outboundBefore = Json.encodeToString(SyncedSubscription.from(original))
        manager.setFeedMuted(listOf(original.uid), true).test().assertComplete().assertNoErrors()
        store.reconcileLocalSubscriptions()
        assertEquals(revisions, store.getKnownRevisions())
        assertEquals(outboundBefore, Json.encodeToString(SyncedSubscription.from(stored(original))))

        // An older peer has neither a mute field nor the optional alert settings.
        val remote = Json.decodeFromString<SyncedSubscription>("""{"serviceId":0,"url":"${original.url}","name":"Remote name"}""")
        val origin = peerId()
        store.applyChanges(
            listOf(
                SubscriptionChange(
                    originPeerId = origin,
                    originRevision = 1,
                    lamportVersion = 100,
                    recordId = SubscriptionRecordId.from(profile, 0, original.url!!),
                    serviceId = 0,
                    url = original.url!!,
                    type = SubscriptionChangeType.UPSERT,
                    subscription = remote
                )
            )
        )
        val updated = stored(original)
        assertEquals("Remote name", updated.name)
        assertTrue(updated.feedMuted)
        assertEquals(original.notificationMode, updated.notificationMode)
        assertEquals(original.notificationKeywords, updated.notificationKeywords)
        val fresh = remote.copy(url = "https://example.com/new-sync")
        store.applyChanges(
            listOf(
                SubscriptionChange(
                    originPeerId = origin,
                    originRevision = 2,
                    lamportVersion = 101,
                    recordId = SubscriptionRecordId.from(profile, 0, fresh.url),
                    serviceId = 0,
                    url = fresh.url,
                    type = SubscriptionChangeType.UPSERT,
                    subscription = fresh
                )
            )
        )
        assertFalse(dao.getSubscriptionDirectForProfile(profile, 0, fresh.url)!!.feedMuted)
    }

    private fun channel(name: String, muted: Boolean = false, profileId: String = profile): SubscriptionEntity = SubscriptionEntity(
        serviceId = 0, url = "https://example.com/$name", name = name, handle = "@$name",
        subscriberCount = 100, notificationMode = NotificationMode.KEYWORDS_ONLY,
        notificationKeywords = "science", youtubeModeMask = 3, feedMuted = muted, profileId = profileId
    ).apply { uid = dao.insert(this) }

    private fun stored(channel: SubscriptionEntity) = requireNotNull(dao.getSubscriptionForProfile(profile, channel.uid))

    private fun channelInfo(url: String) = ChannelInfo(0, "fixture", url, url, "Updated", ListLinkHandler(url, url, "fixture", emptyList(), emptyList())).apply {
        description = "Updated metadata"
        subscriberCount = 200
    }

    private fun peerId() = DeviceIdentity(generateKeyPair(KeyType.ED25519).first).peerId.toBase58()
}
