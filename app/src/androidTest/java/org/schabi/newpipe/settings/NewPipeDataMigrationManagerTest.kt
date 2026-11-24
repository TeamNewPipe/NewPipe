package org.schabi.newpipe.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.export.NewPipeDataMigrationManager
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryConfig

@RunWith(AndroidJUnit4::class)
class NewPipeDataMigrationManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var sourcePath: Path
    private var originalPreferences: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        originalPreferences = buildMap {
            preferences.all.forEach { (key, value) ->
                if (value != null) {
                    put(key, value)
                }
            }
        }
        preferences.edit().clear().commit()
        sourcePath = context.cacheDir.resolve("newpipe-migration-source.db").toPath()
        sourcePath.toFile().delete()
        createSourceDatabase(sourcePath)
    }

    @After
    fun tearDown() {
        NewPipeDatabase.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        restorePreferences()
        sourcePath.toFile().delete()
    }

    @Test
    fun historyProgressAndPlaylistsAreMergedWithoutReplacingExistingData() {
        val target = NewPipeDatabase.getInstance(context)
        target.playlistDAO().insert(
            PlaylistEntity(
                name = "Lessons",
                isThumbnailPermanent = false,
                thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                displayIndex = 0
            )
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath)
        assertEquals(1, preview.historyItems)
        assertEquals(1, preview.progressItems)
        assertEquals(1, preview.playlists)
        assertEquals(1, preview.playlistItems)
        assertEquals(2, preview.subscriptions)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = true,
                importPlaylists = true
            )
        )

        assertEquals(1, result.historyItems)
        assertEquals(1, result.progressItems)
        assertEquals(1, result.playlists)
        assertEquals(1, result.playlistItems)
        assertEquals(1, target.streamDAO().getAll().blockingFirst().size)
        assertEquals(1, target.streamHistoryDAO().getAllDirect().size)
        assertEquals(90_000, target.streamStateDAO().getAllDirect().single().progressMillis)
        val playlists = target.playlistDAO().getAllDirect()
        assertEquals(2, playlists.size)
        assertTrue(playlists.any { it.name == "Lessons (Imported)" })
        val imported = playlists.single { it.name == "Lessons (Imported)" }
        assertEquals(1, target.playlistStreamDAO().getOrderedStreamsDirect(imported.uid).size)
    }

    @Test
    fun importsPortableDataOnlyIntoSelectedProfile() {
        val profileId = "22222222-2222-2222-2222-222222222222"
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = true,
                importPlaylists = true,
                importSubscriptions = true
            ),
            targetProfileId = profileId
        )

        val target = NewPipeDatabase.getInstance(context)
        assertEquals(1, result.historyItems)
        assertEquals(1, result.progressItems)
        assertEquals(1, result.playlists)
        assertEquals(2, result.subscriptions)

        assertTrue(
            target.streamHistoryDAO()
                .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID)
                .isEmpty()
        )
        assertTrue(
            target.streamStateDAO()
                .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID)
                .isEmpty()
        )
        assertTrue(
            target.playlistDAO()
                .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID)
                .isEmpty()
        )
        assertTrue(
            target.subscriptionDAO()
                .getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID)
                .isEmpty()
        )

        assertEquals(1, target.streamHistoryDAO().getAllDirectForProfile(profileId).size)
        assertEquals(1, target.streamStateDAO().getAllDirectForProfile(profileId).size)
        assertEquals(1, target.playlistDAO().getAllDirectForProfile(profileId).size)
        assertEquals(2, target.subscriptionDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun compatibleSubscriptionsAreMergedWithoutReplacingExistingSettings() {
        val target = NewPipeDatabase.getInstance(context)
        target.subscriptionDAO().insert(
            SubscriptionEntity(
                serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
                url = "https://www.youtube.com/channel/existing",
                name = "Local name",
                notificationMode = NotificationMode.ENABLED,
                notificationKeywords = "lessons"
            )
        )
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSubscriptions = true
            )
        )

        val subscriptions = target.subscriptionDAO().getAllDirect()
        assertEquals(1, result.subscriptions)
        assertEquals(1, result.skippedItems)
        assertEquals(2, subscriptions.size)
        val existing = subscriptions.single { it.url!!.endsWith("/existing") }
        assertEquals("Local name", existing.name)
        assertEquals(NotificationMode.ENABLED, existing.notificationMode)
        assertEquals("lessons", existing.notificationKeywords)
        val imported = subscriptions.single { it.url!!.endsWith("/imported") }
        assertEquals("Imported channel", imported.name)
        assertEquals("https://example.com/avatar.jpg", imported.avatarUrl)
        assertEquals(NotificationMode.DISABLED, imported.notificationMode)
    }

    @Test
    fun subscriptionNotificationFlagsAreImportedWithoutImportingGlobalSettings() {
        addSourceNotificationModes()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val notificationsKey = context.getString(R.string.enable_streams_notifications)
        preferences.edit().putBoolean(notificationsKey, false).commit()
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(false, false, importSubscriptions = true)
        )

        val subscriptions = NewPipeDatabase.getInstance(context)
            .subscriptionDAO().getAllDirect()
        assertEquals(2, result.subscriptions)
        assertEquals(0, result.skippedItems)
        assertEquals(
            NotificationMode.ENABLED,
            subscriptions.single { it.url!!.endsWith("/existing") }.notificationMode
        )
        assertEquals(
            NotificationMode.DISABLED,
            subscriptions.single { it.url!!.endsWith("/imported") }.notificationMode
        )
        assertFalse(preferences.getBoolean(notificationsKey, true))
    }

    @Test
    fun reimportRestoresExistingNotificationFlagsOnlyInSelectedProfile() {
        addSourceNotificationModes()
        val profileId = "22222222-2222-2222-2222-222222222222"
        val dao = NewPipeDatabase.getInstance(context).subscriptionDAO()
        val localChannels = listOf("existing", "imported").mapIndexed { index, suffix ->
            val entity = SubscriptionEntity(
                serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
                url = "https://www.youtube.com/channel/$suffix",
                name = "Local $suffix",
                avatarUrl = "https://example.com/local-avatar.jpg",
                subscriberCount = 123,
                description = "Local description",
                notificationMode = if (index == 0) {
                    NotificationMode.DISABLED
                } else {
                    NotificationMode.ENABLED
                },
                notificationKeywords = "lessons",
                youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC,
                profileId = profileId,
                handle = "@local-$suffix"
            )
            entity.copy(uid = dao.insert(entity))
        }
        val otherProfileChannels = localChannels.map { channel ->
            val entity = channel.copy(uid = 0, profileId = ProfileManager.DEFAULT_PROFILE_ID)
            entity.copy(uid = dao.insert(entity))
        }
        val manager = NewPipeDataMigrationManager(context)
        val selection = NewPipeDataMigrationManager.Selection(
            false,
            false,
            importSubscriptions = true
        )

        val result = manager.importData(sourcePath, selection, targetProfileId = profileId)

        assertEquals(2, result.subscriptions)
        assertEquals(0, result.skippedItems)
        assertEquals(
            localChannels.mapIndexed { index, channel ->
                channel.copy(
                    notificationMode = if (index == 0) {
                        NotificationMode.ENABLED
                    } else {
                        NotificationMode.DISABLED
                    }
                )
            },
            dao.getAllDirectForProfile(profileId).sortedBy { it.uid }
        )
        assertEquals(
            otherProfileChannels,
            dao.getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).sortedBy { it.uid }
        )

        val repeated = manager.importData(sourcePath, selection, targetProfileId = profileId)
        assertEquals(0, repeated.subscriptions)
        assertEquals(2, repeated.skippedItems)
        assertEquals(4, dao.getAllDirect().size)
    }

    @Test
    fun absentNotificationFlagsPreserveExistingModesWhenReimporting() {
        val dao = NewPipeDatabase.getInstance(context).subscriptionDAO()
        val local = SubscriptionEntity(
            serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
            url = "https://www.youtube.com/channel/existing",
            name = "Local channel",
            notificationMode = NotificationMode.KEYWORDS_ONLY,
            notificationKeywords = "lessons"
        )
        val localId = dao.insert(local)

        NewPipeDataMigrationManager(context).importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(false, false, importSubscriptions = true)
        )

        assertEquals(local.copy(uid = localId), dao.getSubscription(localId))
        assertEquals(
            NotificationMode.DISABLED,
            dao.getSubscriptionDirect(0, "https://www.youtube.com/channel/imported")!!
                .notificationMode
        )
    }

    @Test
    fun unrecognizedOrNullNotificationFlagsDoNotOverwriteExistingModes() {
        addSourceNotificationModes()
        val dao = NewPipeDatabase.getInstance(context).subscriptionDAO()
        val manager = NewPipeDataMigrationManager(context)
        val invalidModes = listOf<Any?>(null, -1, 2, 99, 4_294_967_297L, 1.5, "enabled")
        for (mode in invalidModes) {
            dao.deleteAll()
            val local = SubscriptionEntity(
                serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
                url = "https://www.youtube.com/channel/existing",
                name = "Local channel",
                notificationMode = NotificationMode.KEYWORDS_ONLY,
                notificationKeywords = "lessons"
            )
            val localId = dao.insert(local)
            SQLiteDatabase.openDatabase(
                sourcePath.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { source ->
                source.execSQL(
                    "UPDATE subscriptions SET notification_mode = ?",
                    arrayOf(mode)
                )
            }

            val result = manager.importData(
                sourcePath,
                NewPipeDataMigrationManager.Selection(false, false, importSubscriptions = true)
            )

            assertEquals(1, result.subscriptions)
            assertEquals(1, result.skippedItems)
            assertEquals(local.copy(uid = localId), dao.getSubscription(localId))
            assertEquals(
                NotificationMode.DISABLED,
                dao.getSubscriptionDirect(0, "https://www.youtube.com/channel/imported")!!
                    .notificationMode
            )
        }
    }

    @Test
    fun deselectingSubscriptionsDoesNotRestoreNotificationFlags() {
        addSourceNotificationModes()
        val dao = NewPipeDatabase.getInstance(context).subscriptionDAO()
        val localId = dao.insert(
            SubscriptionEntity(
                serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
                url = "https://www.youtube.com/channel/existing",
                name = "Local channel",
                notificationMode = NotificationMode.DISABLED
            )
        )

        val result = NewPipeDataMigrationManager(context).importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(true, true)
        )

        assertEquals(0, result.subscriptions)
        assertEquals(NotificationMode.DISABLED, dao.getSubscription(localId).notificationMode)
        assertEquals(1, dao.getAllDirect().size)
    }

    @Test
    fun failedMigrationRollsBackRestoredNotificationFlags() {
        addSourceNotificationModes()
        val target = NewPipeDatabase.getInstance(context)
        val dao = target.subscriptionDAO()
        val localId = dao.insert(
            SubscriptionEntity(
                serviceId = SubscriptionEntity.YOUTUBE_SERVICE_ID,
                url = "https://www.youtube.com/channel/existing",
                name = "Local channel",
                notificationMode = NotificationMode.DISABLED
            )
        )
        val writable = target.openHelper.writableDatabase
        writable.execSQL(
            "CREATE TRIGGER fail_playlist_import BEFORE INSERT ON playlists " +
                "BEGIN SELECT RAISE(ABORT, 'rollback imported notification modes'); END"
        )

        val result = try {
            runCatching {
                NewPipeDataMigrationManager(context).importData(
                    sourcePath,
                    NewPipeDataMigrationManager.Selection(false, true, importSubscriptions = true)
                )
            }
        } finally {
            writable.execSQL("DROP TRIGGER fail_playlist_import")
        }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message.orEmpty()
                .contains("rollback imported notification modes")
        )
        assertEquals(NotificationMode.DISABLED, dao.getSubscription(localId).notificationMode)
        assertEquals(1, dao.getAllDirect().size)
    }

    @Test
    fun groupsMergeMembershipsUsingRemappedIdsAndRemainIdempotent() {
        val target = NewPipeDatabase.getInstance(context)
        val existingChannel = target.subscriptionDAO().insert(
            SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/channel/existing", name = "Existing")
        )
        val localChannel = target.subscriptionDAO().insert(
            SubscriptionEntity(serviceId = 0, url = "https://www.youtube.com/channel/local", name = "Local")
        )
        val groups = target.feedGroupDAO()
        val existingGroup = groups.insert(FeedGroupEntity(0, "Lessons", FeedGroupIcon.EDUCATION))
        groups.insertSubscriptionsToGroup(listOf(FeedGroupSubscriptionEntity(existingGroup, localChannel)))
        SQLiteDatabase.openDatabase(sourcePath.toString(), null, SQLiteDatabase.OPEN_READWRITE).use { source ->
            source.execSQL("CREATE TABLE feed_group (uid INTEGER PRIMARY KEY, name TEXT, icon_id INTEGER, sort_order INTEGER)")
            source.execSQL("CREATE TABLE feed_group_subscription_join (group_id INTEGER, subscription_id INTEGER)")
            source.execSQL("INSERT INTO feed_group VALUES (90, 'Lessons', 2, 0), (91, 'Empty', 999, 1)")
            source.execSQL("INSERT INTO feed_group_subscription_join VALUES (90, 1), (90, 2), (90, 999)")
        }
        val manager = NewPipeDataMigrationManager(context)
        assertEquals(2, manager.inspect(sourcePath).channelGroups)
        val selection = NewPipeDataMigrationManager.Selection(false, false, importSubscriptions = true)
        assertEquals(1, manager.importData(sourcePath, selection).channelGroups)
        assertEquals(0, manager.importData(sourcePath, selection).channelGroups)
        val importedChannel = target.subscriptionDAO().getSubscriptionDirect(0, "https://www.youtube.com/channel/imported")!!.uid
        assertEquals(setOf(existingChannel, localChannel, importedChannel), groups.getSubscriptionIdsForDirect(existingGroup).toSet())
        val allGroups = groups.getAllDirect()
        assertEquals(listOf("Lessons", "Empty"), allGroups.map { it.name })
        assertEquals(FeedGroupIcon.ALL, allGroups.last().icon)
        assertTrue(groups.getSubscriptionIdsForDirect(allGroups.last().uid).isEmpty())
    }

    @Test
    fun deselectingSubscriptionsAlsoLeavesGroupsUntouched() {
        SQLiteDatabase.openDatabase(sourcePath.toString(), null, SQLiteDatabase.OPEN_READWRITE).use { source ->
            source.execSQL("CREATE TABLE feed_group (uid INTEGER PRIMARY KEY, name TEXT, icon_id INTEGER, sort_order INTEGER)")
            source.execSQL("INSERT INTO feed_group VALUES (1, 'Group', 0, 0)")
        }
        NewPipeDataMigrationManager(context).importData(sourcePath, NewPipeDataMigrationManager.Selection(true, true))
        assertTrue(NewPipeDatabase.getInstance(context).feedGroupDAO().getAllDirect().isEmpty())
    }

    @Test
    fun subscriptionOnlyBackupIsAccepted() {
        sourcePath.toFile().delete()
        createSubscriptionOnlySourceDatabase(sourcePath)
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath)
        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSubscriptions = true
            )
        )

        assertEquals(1, preview.subscriptions)
        assertFalse(preview.hasHistory)
        assertFalse(preview.hasPlaylists)
        assertEquals(1, result.subscriptions)
    }

    @Test
    fun unselectedHistoryIsNotImportedWithPlaylists() {
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = true
            )
        )

        val target = NewPipeDatabase.getInstance(context)
        assertEquals(0, result.historyItems)
        assertEquals(0, result.progressItems)
        assertTrue(target.streamHistoryDAO().getAllDirect().isEmpty())
        assertTrue(target.streamStateDAO().getAllDirect().isEmpty())
        assertEquals(1, target.playlistDAO().getAllDirect().size)
    }

    @Test
    fun pipePipePlaylistAndStreamOrderMatchesItsVisibleOrder() {
        sourcePath.toFile().delete()
        createPipePipeSourceDatabase(sourcePath)
        val manager = NewPipeDataMigrationManager(context)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = true
            )
        )

        val target = NewPipeDatabase.getInstance(context)
        assertEquals(2, result.playlists)
        assertEquals(4, result.playlistItems)
        val playlists = target.playlistDAO().getAllDirect().sortedBy { it.displayIndex }
        assertEquals(listOf("Alpha", "Zulu"), playlists.map { it.name })
        val alphaStreams = target.playlistStreamDAO()
            .getOrderedStreamsDirect(playlists.first().uid)
        assertEquals(
            listOf("First lesson", "Second lesson", "Third lesson"),
            alphaStreams.map { it.title }
        )
    }

    @Test
    fun compatibleSettingsAreTypeCheckedAndMergedWithoutClearingOtherPreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString("existing_wizestream_setting", "keep me")
            .putBoolean(context.getString(R.string.show_comments_key), true)
            .commit()
        val sourcePreferences = mapOf<String, Any>(
            context.getString(R.string.current_service_key) to "YouTube",
            context.getString(R.string.show_comments_key) to false,
            context.getString(R.string.playback_speed_key) to 1.25f,
            context.getString(R.string.show_next_video_key) to "wrong type",
            context.getString(R.string.proxy_password_key) to "secret"
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath, sourcePreferences)
        assertEquals(2, preview.compatibleSettings)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSettings = true
            ),
            sourcePreferences
        )

        assertEquals(2, result.compatibleSettings)
        assertFalse(preferences.contains("service"))
        assertFalse(preferences.getBoolean("show_comments", true))
        assertEquals(1.25f, preferences.getFloat("playback_speed_key", 0f))
        assertFalse(preferences.contains("show_next_video"))
        assertFalse(preferences.contains("proxy_password"))
        assertEquals("keep me", preferences.getString("existing_wizestream_setting", null))
    }

    @Test
    fun pipePipeSponsorBlockSettingsAreConvertedWithoutInventingMissingDefaults() {
        sourcePath.toFile().delete()
        createPipePipeSourceDatabase(sourcePath)
        val sourcePreferences = mapOf<String, Any>(
            context.getString(R.string.sponsor_block_enable_key) to true,
            context.getString(R.string.sponsor_block_notifications_key) to false,
            "sponsor_block_category_sponsor" to true,
            "sponsor_block_category_intro" to false,
            "sponsor_block_category_sponsor_mode" to "automatic",
            "sponsor_block_category_intro_mode" to "manual",
            "sponsor_block_category_outro_mode" to "highlight",
            "sponsor_block_category_sponsor_color" to "#123456",
            context.getString(R.string.sponsor_block_user_id_key) to "do not import"
        )
        val manager = NewPipeDataMigrationManager(context)

        val preview = manager.inspect(sourcePath, sourcePreferences)
        assertEquals(NewPipeDataMigrationManager.SourceApp.PIPEPIPE, preview.sourceApp)
        assertEquals(8, preview.sponsorBlockSettings)

        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(
                importHistory = false,
                importPlaylists = false,
                importSettings = false,
                importSponsorBlock = true
            ),
            sourcePreferences
        )

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(8, result.sponsorBlockSettings)
        assertTrue(preferences.getBoolean("sponsor_block_enable", false))
        assertFalse(preferences.getBoolean("sponsor_block_notifications", true))
        assertFalse(preferences.contains("sponsor_block_graced_rewind"))
        assertTrue(preferences.getBoolean("sponsor_block_category_sponsor", false))
        assertFalse(preferences.getBoolean("sponsor_block_category_intro", true))
        assertEquals(
            SponsorBlockBehavior.SKIP.value,
            preferences.getString(SponsorBlockCategoryConfig.SPONSOR.behaviorKey(), null)
        )
        assertEquals(
            SponsorBlockBehavior.MANUAL.value,
            preferences.getString(SponsorBlockCategoryConfig.INTRO.behaviorKey(), null)
        )
        assertEquals(
            SponsorBlockBehavior.DONT_SKIP.value,
            preferences.getString(SponsorBlockCategoryConfig.OUTRO.behaviorKey(), null)
        )
        assertEquals(
            0xFF123456L,
            preferences.getLong(SponsorBlockCategoryConfig.SPONSOR.colorKey(), 0L)
        )
        assertFalse(preferences.contains("sponsor_block_user_id"))
    }

    @Test
    fun searchHistoryIsImportedIntoSelectedProfileWithoutDuplicateEvents() {
        val otherProfile = "22222222-2222-2222-2222-222222222222"
        val manager = NewPipeDataMigrationManager(context)
        val preview = manager.inspect(sourcePath)
        assertEquals(2, preview.searchHistoryItems)

        val selection = NewPipeDataMigrationManager.Selection(
            importHistory = true,
            importPlaylists = false
        )
        val first = manager.importData(sourcePath, selection, targetProfileId = otherProfile)
        assertEquals(2, first.searchHistoryItems)
        val searchDao = NewPipeDatabase.getInstance(context).searchHistoryDAO()
        assertTrue(searchDao.getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).isEmpty())
        assertEquals(
            listOf("zaatar recipe", "spice recipe"),
            searchDao.getAllDirectForProfile(otherProfile).map { it.search }
        )

        val second = manager.importData(sourcePath, selection, targetProfileId = otherProfile)
        assertEquals(0, second.searchHistoryItems)
        assertEquals(2, searchDao.getAllDirectForProfile(otherProfile).size)

        val defaultProfile = manager.importData(sourcePath, selection)
        assertEquals(2, defaultProfile.searchHistoryItems)
        assertEquals(2, searchDao.getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).size)
    }

    @Test
    fun unavailablePluginRecordsArePreservedInTheirImportedProfile() {
        val pluginServiceId = 1_234_567
        val profileId = "22222222-2222-2222-2222-222222222222"
        assertTrue(org.schabi.newpipe.extractor.ServiceList.all().none { it.serviceId == pluginServiceId })
        SQLiteDatabase.openDatabase(
            sourcePath.toString(),
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { source ->
            source.execSQL("UPDATE streams SET service_id = ?", arrayOf(pluginServiceId))
            source.execSQL("UPDATE subscriptions SET service_id = ?", arrayOf(pluginServiceId))
            source.execSQL("UPDATE search_history SET service_id = ?", arrayOf(pluginServiceId))
        }
        val manager = NewPipeDataMigrationManager(context)
        val selection = NewPipeDataMigrationManager.Selection(
            importHistory = true,
            importPlaylists = true,
            importSubscriptions = true
        )

        val result = manager.importData(sourcePath, selection, targetProfileId = profileId)

        val database = NewPipeDatabase.getInstance(context)
        assertEquals(2, result.subscriptions)
        assertEquals(2, result.searchHistoryItems)
        assertEquals(1, result.historyItems)
        assertEquals(1, result.playlistItems)
        assertEquals(
            setOf(pluginServiceId),
            database.subscriptionDAO()
                .getAllDirectForProfile(profileId).map { it.serviceId }.toSet()
        )
        assertEquals(
            setOf(pluginServiceId),
            database.searchHistoryDAO()
                .getAllDirectForProfile(profileId).map { it.serviceId }.toSet()
        )
        assertEquals(pluginServiceId, database.streamDAO().getAll().blockingFirst().single().serviceId)
        assertTrue(database.subscriptionDAO().getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).isEmpty())
        assertTrue(database.searchHistoryDAO().getAllDirectForProfile(ProfileManager.DEFAULT_PROFILE_ID).isEmpty())
        val repeated = manager.importData(sourcePath, selection, targetProfileId = profileId)
        assertEquals(0, repeated.subscriptions)
        assertEquals(0, repeated.searchHistoryItems)
        assertEquals(2, database.subscriptionDAO().getAllDirectForProfile(profileId).size)
    }

    @Test
    fun searchOnlyBackupCanBeInspectedAndImported() {
        SQLiteDatabase.openDatabase(
            sourcePath.toString(),
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { source ->
            source.execSQL("DROP TABLE stream_history")
            source.execSQL("DROP TABLE stream_state")
            source.execSQL("DROP TABLE playlist_stream_join")
            source.execSQL("DROP TABLE playlists")
            source.execSQL("DROP TABLE subscriptions")
            source.execSQL("DROP TABLE streams")
        }
        val manager = NewPipeDataMigrationManager(context)
        val preview = manager.inspect(sourcePath)
        assertEquals(2, preview.searchHistoryItems)
        assertTrue(preview.hasHistory)
        assertTrue(preview.hasImportableData)
        val result = manager.importData(
            sourcePath,
            NewPipeDataMigrationManager.Selection(importHistory = true, importPlaylists = false)
        )
        assertEquals(2, result.searchHistoryItems)
        assertEquals(2, NewPipeDatabase.getInstance(context).searchHistoryDAO().getAllDirect().size)
    }

    private fun restorePreferences() {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit())
    }

    private fun addSourceNotificationModes() {
        SQLiteDatabase.openDatabase(
            sourcePath.toString(),
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { source ->
            source.execSQL("ALTER TABLE subscriptions ADD COLUMN notification_mode INTEGER")
            source.execSQL("UPDATE subscriptions SET notification_mode = 1 WHERE uid = 1")
            source.execSQL("UPDATE subscriptions SET notification_mode = 0 WHERE uid = 2")
        }
    }

    private fun createSourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { source ->
            source.execSQL(
                "CREATE TABLE streams (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, " +
                    "duration INTEGER NOT NULL, uploader TEXT NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE search_history (" +
                    "id INTEGER PRIMARY KEY, creation_date INTEGER, " +
                    "service_id INTEGER, search TEXT)"
            )
            source.execSQL(
                "INSERT INTO search_history VALUES " +
                    "(1, 1788253200000, 0, 'zaatar recipe'), " +
                    "(2, 1788253201000, 0, 'spice recipe')"
            )
            source.execSQL(
                "CREATE TABLE stream_history (" +
                    "stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, " +
                    "repeat_count INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE stream_state (" +
                    "stream_id INTEGER PRIMARY KEY, progress_time INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlists (uid INTEGER PRIMARY KEY, name TEXT)"
            )
            source.execSQL(
                "CREATE TABLE playlist_stream_join (" +
                    "playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, " +
                    "join_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE subscriptions (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, name TEXT, avatar_url TEXT, " +
                    "subscriber_count INTEGER, description TEXT)"
            )
            source.execSQL(
                "INSERT INTO streams VALUES " +
                    "(1, 0, 'https://example.com/watch/1', 'Lesson one', " +
                    "'VIDEO_STREAM', 300, 'Teacher')"
            )
            source.execSQL("INSERT INTO stream_history VALUES (1, 1788253200000, 2)")
            source.execSQL("INSERT INTO stream_state VALUES (1, 90000)")
            source.execSQL("INSERT INTO playlists VALUES (10, 'Lessons')")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (10, 1, 0)")
            source.execSQL(
                "INSERT INTO subscriptions VALUES " +
                    "(1, 0, 'https://www.youtube.com/channel/existing', " +
                    "'Backup name', NULL, NULL, NULL), " +
                    "(2, 0, 'https://www.youtube.com/channel/imported', " +
                    "'Imported channel', 'https://example.com/avatar.jpg', 42, 'Description')"
            )
        }
    }

    private fun createSubscriptionOnlySourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { source ->
            source.execSQL(
                "CREATE TABLE subscriptions (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, name TEXT)"
            )
            source.execSQL(
                "INSERT INTO subscriptions VALUES " +
                    "(1, 0, 'https://www.youtube.com/channel/imported', 'Imported channel')"
            )
        }
    }

    private fun createPipePipeSourceDatabase(path: Path) {
        SQLiteDatabase.openOrCreateDatabase(path.toFile(), null).use { source ->
            source.execSQL("PRAGMA user_version = 901")
            source.execSQL(
                "CREATE TABLE streams (" +
                    "uid INTEGER PRIMARY KEY, service_id INTEGER NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, stream_type TEXT NOT NULL, " +
                    "duration INTEGER NOT NULL, uploader TEXT NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlists (" +
                    "uid INTEGER PRIMARY KEY, name TEXT, display_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE playlist_stream_join (" +
                    "playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, " +
                    "join_index INTEGER NOT NULL)"
            )
            source.execSQL(
                "CREATE TABLE sponsorblock_whitelist (uploader TEXT PRIMARY KEY NOT NULL)"
            )
            source.execSQL(
                "INSERT INTO streams VALUES " +
                    "(1, 0, 'https://example.com/watch/1', 'First lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher'), " +
                    "(2, 0, 'https://example.com/watch/2', 'Second lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher'), " +
                    "(3, 0, 'https://example.com/watch/3', 'Third lesson', " +
                    "'VIDEO_STREAM', 300, 'Teacher')"
            )
            source.execSQL("INSERT INTO playlists VALUES (10, 'Zulu', 0), (20, 'Alpha', 1)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 3, 2)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 1, 0)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (20, 2, 1)")
            source.execSQL("INSERT INTO playlist_stream_join VALUES (10, 2, 0)")
        }
    }
}
