package org.schabi.newpipe.settings.export

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.SavedServicePolicy

class NewPipeDataMigrationManager(private val context: Context) {
    enum class SourceApp {
        NEWPIPE,
        PIPEPIPE
    }

    data class Preview(
        val historyItems: Int,
        val progressItems: Int,
        val playlists: Int,
        val playlistItems: Int,
        val subscriptions: Int,
        val compatibleSettings: Int,
        val sponsorBlockSettings: Int,
        val sourceApp: SourceApp,
        val channelGroups: Int = 0,
        val searchHistoryItems: Int = 0
    ) {
        val hasHistory: Boolean
            get() = historyItems > 0 || progressItems > 0 || searchHistoryItems > 0
        val hasPlaylists: Boolean
            get() = playlists > 0
        val hasSubscriptions: Boolean
            get() = subscriptions > 0 || channelGroups > 0
        val hasCompatibleSettings: Boolean
            get() = compatibleSettings > 0
        val hasSponsorBlockSettings: Boolean
            get() = sponsorBlockSettings > 0
        val hasImportableData: Boolean
            get() = hasHistory || hasPlaylists || hasSubscriptions ||
                hasCompatibleSettings || hasSponsorBlockSettings
    }

    data class Selection @JvmOverloads constructor(
        val importHistory: Boolean,
        val importPlaylists: Boolean,
        val importSettings: Boolean = false,
        val importSponsorBlock: Boolean = false,
        val importSubscriptions: Boolean = false
    )

    data class Result(
        val historyItems: Int,
        val progressItems: Int,
        val playlists: Int,
        val playlistItems: Int,
        val subscriptions: Int,
        val compatibleSettings: Int,
        val sponsorBlockSettings: Int,
        val skippedItems: Int,
        val channelGroups: Int = 0,
        val searchHistoryItems: Int = 0
    )

    class UnsupportedSourceException(message: String) : Exception(message)

    @JvmOverloads
    fun inspect(
        databasePath: Path,
        sourcePreferences: Map<String, *> = emptyMap<String, Any>()
    ): Preview = openSource(databasePath).use { source ->
        val schema = inspectSchema(source)
        val compatibleSettings = CompatibleSettingsMigration(context).prepare(sourcePreferences)
        val sponsorBlockSettings = if (schema.isPipePipe) {
            PipePipeSponsorBlockMigration(context).prepare(sourcePreferences)
        } else {
            CompatibleSettingsMigration.Prepared(emptyMap<String, Any>())
        }
        Preview(
            historyItems = if (schema.hasHistory) source.countRows(HISTORY_TABLE) else 0,
            progressItems = if (schema.hasProgress) source.countRows(STATE_TABLE) else 0,
            playlists = if (schema.hasPlaylists) source.countRows(PLAYLIST_TABLE) else 0,
            playlistItems = if (schema.hasPlaylists) source.countRows(PLAYLIST_JOIN_TABLE) else 0,
            subscriptions = if (schema.hasSubscriptions) {
                source.countRows(SUBSCRIPTION_TABLE)
            } else {
                0
            },
            compatibleSettings = compatibleSettings.size,
            sponsorBlockSettings = sponsorBlockSettings.size,
            sourceApp = if (schema.isPipePipe) SourceApp.PIPEPIPE else SourceApp.NEWPIPE,
            channelGroups = if (schema.hasGroups) source.countRows(GROUP_TABLE) else 0,
            searchHistoryItems = if (schema.hasSearchHistory) source.countRows(SEARCH_HISTORY_TABLE) else 0
        )
    }

    @JvmOverloads
    fun importData(
        databasePath: Path,
        selection: Selection,
        sourcePreferences: Map<String, *> = emptyMap<String, Any>(),
        targetProfileId: String = ProfileManager.DEFAULT_PROFILE_ID
    ): Result = openSource(databasePath).use { source ->
        val schema = inspectSchema(source)
        val streams = if (selection.importPlaylists ||
            (selection.importHistory && (schema.hasHistory || schema.hasProgress))
        ) {
            readStreams(source)
        } else {
            emptyMap()
        }
        val target = NewPipeDatabase.getInstance(context)
        val settingsMigration = CompatibleSettingsMigration(context)
        val compatibleSettings = settingsMigration.prepare(sourcePreferences)
        val sponsorBlockSettings = if (schema.isPipePipe) {
            PipePipeSponsorBlockMigration(context).prepare(sourcePreferences)
        } else {
            CompatibleSettingsMigration.Prepared(emptyMap<String, Any>())
        }
        var settingsRollback: CompatibleSettingsMigration.Rollback? = null
        var sponsorBlockRollback: CompatibleSettingsMigration.Rollback? = null
        var result = Result(0, 0, 0, 0, 0, 0, 0, 0)

        try {
            if (selection.importSettings && compatibleSettings.size > 0) {
                settingsRollback = settingsMigration.apply(compatibleSettings)
            }
            if (selection.importSponsorBlock && sponsorBlockSettings.size > 0) {
                sponsorBlockRollback = settingsMigration.apply(sponsorBlockSettings)
            }
            target.runInTransaction {
                val streamIds = mutableMapOf<Long, Long>()
                fun targetStreamId(sourceId: Long): Long? {
                    streamIds[sourceId]?.let { return it }
                    val stream = streams[sourceId] ?: return null
                    val streamDao = target.streamDAO()
                    val existing = streamDao.getStreamDirect(stream.serviceId, stream.url)
                    val targetId = existing?.uid ?: streamDao.insert(stream)
                    streamIds[sourceId] = targetId
                    return targetId
                }

                var historyItems = 0
                var searchHistoryItems = 0
                var progressItems = 0
                var playlists = 0
                var playlistItems = 0
                var subscriptions = 0
                var channelGroups = 0
                val subscriptionIds = mutableMapOf<Long, Long>()
                var skippedItems = 0

                if (selection.importSubscriptions && schema.hasSubscriptions) {
                    val supportedServiceIds = ServiceList.all()
                        .mapTo(mutableSetOf()) { it.serviceId }
                    source.rawQuery("SELECT * FROM $SUBSCRIPTION_TABLE", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val serviceId = cursor.long("service_id")?.toInt()
                            val url = cursor.string("url")?.trim().orEmpty()
                            if (serviceId == null || !SavedServicePolicy.canPreserve(serviceId, supportedServiceIds) ||
                                url.isEmpty()
                            ) {
                                skippedItems++
                                continue
                            }
                            val notificationMode = cursor.notificationMode()
                            val entity = SubscriptionEntity(
                                serviceId = serviceId,
                                url = url,
                                name = cursor.string("name")?.trim().orEmpty().ifEmpty { url },
                                avatarUrl = cursor.string("avatar_url"),
                                subscriberCount = cursor.long("subscriber_count"),
                                description = cursor.string("description"),
                                notificationMode = notificationMode ?: NotificationMode.DISABLED,
                                profileId = targetProfileId
                            )
                            val insertedId = target.subscriptionDAO().insertIgnore(entity)
                            val targetId = if (insertedId != -1L) {
                                subscriptions++
                                insertedId
                            } else {
                                val existing = target.subscriptionDAO().getSubscriptionDirectForProfile(
                                    targetProfileId,
                                    serviceId,
                                    url
                                )
                                // Re-import can repair flags omitted by earlier migrations without
                                // replacing local channel metadata or notification keywords.
                                if (existing != null && notificationMode != null &&
                                    existing.notificationMode != notificationMode
                                ) {
                                    target.subscriptionDAO().update(
                                        existing.copy(notificationMode = notificationMode)
                                    )
                                    subscriptions++
                                } else {
                                    skippedItems++
                                }
                                existing?.uid
                            }
                            val sourceId = cursor.long("uid")
                            if (sourceId != null && targetId != null) subscriptionIds[sourceId] = targetId
                        }
                    }
                }

                if (selection.importSubscriptions && schema.hasGroups) {
                    val groupDao = target.feedGroupDAO()
                    val existingGroups = groupDao
                        .getAllDirectForProfile(targetProfileId)
                        .toMutableList()
                    val groupIds = mutableMapOf<Long, Long>()
                    source.rawQuery("SELECT * FROM $GROUP_TABLE ORDER BY sort_order, uid", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val sourceId = cursor.long("uid") ?: continue
                            val name = cursor.string("name")?.trim().orEmpty()
                            if (name.isEmpty()) {
                                skippedItems++
                                continue
                            }
                            val iconId = cursor.long("icon_id")?.toInt()
                            val icon = FeedGroupIcon.entries.firstOrNull { it.id == iconId } ?: FeedGroupIcon.ALL
                            val existing = existingGroups.firstOrNull { it.name == name && it.icon == icon }
                            val targetId = existing?.uid ?: run {
                                val group = FeedGroupEntity(
                                    uid = 0,
                                    name = name,
                                    icon = icon,
                                    profileId = targetProfileId
                                )
                                val id = groupDao.insert(group)
                                existingGroups.add(group.copy(uid = id))
                                channelGroups++
                                id
                            }
                            groupIds[sourceId] = targetId
                        }
                    }
                    if (schema.hasGroupMemberships) {
                        source.rawQuery("SELECT group_id, subscription_id FROM $GROUP_JOIN_TABLE", null).use { cursor ->
                            while (cursor.moveToNext()) {
                                val groupId = groupIds[cursor.getLong(0)]
                                val subscriptionId = subscriptionIds[cursor.getLong(1)]
                                if (groupId == null || subscriptionId == null) {
                                    skippedItems++
                                } else {
                                    groupDao.insertSubscriptionsToGroup(listOf(FeedGroupSubscriptionEntity(groupId, subscriptionId)))
                                }
                            }
                        }
                    }
                }

                if (selection.importHistory && schema.hasHistory) {
                    source.rawQuery(
                        "SELECT stream_id, access_date, repeat_count FROM $HISTORY_TABLE",
                        null
                    ).use { cursor ->
                        val writable = target.openHelper.writableDatabase
                        while (cursor.moveToNext()) {
                            val targetId = targetStreamId(cursor.getLong(0))
                            val accessDate = cursor.getLong(1)
                            if (targetId == null || accessDate <= 0) {
                                skippedItems++
                                continue
                            }
                            val repeatCount = cursor.getLong(2).coerceAtLeast(0)
                            writable.execSQL(
                                "INSERT OR IGNORE INTO $HISTORY_TABLE " +
                                    "(stream_id, access_date, repeat_count, profile_id) " +
                                    "VALUES (?, ?, ?, ?)",
                                arrayOf<Any>(
                                    targetId,
                                    accessDate,
                                    repeatCount,
                                    targetProfileId
                                )
                            )
                            writable.execSQL(
                                "UPDATE $HISTORY_TABLE SET repeat_count = " +
                                    "MAX(repeat_count, ?) WHERE profile_id = ? " +
                                    "AND stream_id = ? AND access_date = ?",
                                arrayOf<Any>(
                                    repeatCount,
                                    targetProfileId,
                                    targetId,
                                    accessDate
                                )
                            )
                            historyItems++
                        }
                    }
                }

                if (selection.importHistory && schema.hasSearchHistory) {
                    val searchDao = target.searchHistoryDAO()
                    val validServices = ServiceList.all().mapTo(mutableSetOf()) { it.serviceId }
                    source.rawQuery(
                        "SELECT creation_date, service_id, search FROM $SEARCH_HISTORY_TABLE",
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cursor.isNull(0) || cursor.isNull(1) || cursor.isNull(2)) {
                                skippedItems++
                                continue
                            }
                            val createdAt = cursor.getLong(0)
                            val serviceId = cursor.getInt(1)
                            val search = cursor.getString(2)
                            val timestamp = runCatching { createdAt.toOffsetDateTime() }.getOrNull()
                            if (createdAt <= 0 || !SavedServicePolicy.canPreserve(serviceId, validServices) ||
                                search.isNullOrBlank() || timestamp == null
                            ) {
                                skippedItems++
                                continue
                            }
                            if (searchDao.hasTakeoutEventForProfile(
                                    targetProfileId,
                                    serviceId,
                                    search,
                                    timestamp
                                )
                            ) {
                                skippedItems++
                                continue
                            }
                            searchDao.insert(
                                SearchHistoryEntry(
                                    creationDate = timestamp,
                                    serviceId = serviceId,
                                    search = search,
                                    profileId = targetProfileId
                                )
                            )
                            searchHistoryItems++
                        }
                    }
                }

                if (selection.importHistory && schema.hasProgress) {
                    val existingStates = target.streamStateDAO()
                        .getAllDirectForProfile(targetProfileId)
                        .associateBy { it.streamUid }
                    source.rawQuery(
                        "SELECT stream_id, progress_time FROM $STATE_TABLE",
                        null
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val targetId = targetStreamId(cursor.getLong(0))
                            if (targetId == null) {
                                skippedItems++
                                continue
                            }
                            val progress = cursor.getLong(1).coerceAtLeast(0)
                            val currentProgress = existingStates[targetId]?.progressMillis ?: -1
                            if (progress > currentProgress) {
                                target.streamStateDAO().upsert(
                                    StreamStateEntity(targetId, progress, targetProfileId)
                                )
                                progressItems++
                            }
                        }
                    }
                }

                if (selection.importPlaylists && schema.hasPlaylists) {
                    val playlistDao = target.playlistDAO()
                    val playlistStreamDao = target.playlistStreamDAO()
                    val existingPlaylists = playlistDao.getAllDirectForProfile(targetProfileId)
                    val usedNames = existingPlaylists
                        .mapNotNullTo(mutableSetOf()) { it.name }
                    var displayIndex = existingPlaylists
                        .maxOfOrNull { it.displayIndex }?.plus(1) ?: 0L
                    val playlistOrder = when {
                        schema.usesAlphabeticalPlaylistOrder -> "name COLLATE NOCASE ASC, uid"
                        schema.playlistColumns.contains("display_index") -> "display_index, uid"
                        else -> "uid"
                    }

                    source.rawQuery(
                        "SELECT uid, name FROM $PLAYLIST_TABLE ORDER BY $playlistOrder",
                        null
                    ).use { playlistCursor ->
                        while (playlistCursor.moveToNext()) {
                            val sourcePlaylistId = playlistCursor.getLong(0)
                            val sourceName = playlistCursor.getString(1)?.trim().orEmpty()
                            val targetName = uniquePlaylistName(
                                sourceName.ifBlank { "Imported playlist" },
                                usedNames
                            )
                            val playlist = PlaylistEntity(
                                name = targetName,
                                isThumbnailPermanent = false,
                                thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                                displayIndex = displayIndex++,
                                profileId = targetProfileId
                            )
                            val targetPlaylistId = playlistDao.insert(playlist)
                            var targetIndex = 0
                            var firstStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID

                            source.rawQuery(
                                "SELECT stream_id FROM $PLAYLIST_JOIN_TABLE " +
                                    "WHERE playlist_id = ? ORDER BY join_index",
                                arrayOf(sourcePlaylistId.toString())
                            ).use { itemCursor ->
                                while (itemCursor.moveToNext()) {
                                    val targetId = targetStreamId(itemCursor.getLong(0))
                                    if (targetId == null) {
                                        skippedItems++
                                        continue
                                    }
                                    if (firstStreamId == PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                                        firstStreamId = targetId
                                    }
                                    playlistStreamDao.insert(
                                        PlaylistStreamEntity(
                                            targetPlaylistId,
                                            targetId,
                                            targetIndex++
                                        )
                                    )
                                    playlistItems++
                                }
                            }
                            if (firstStreamId != PlaylistEntity.DEFAULT_THUMBNAIL_ID) {
                                playlist.thumbnailStreamId = firstStreamId
                                playlist.uid = targetPlaylistId
                                playlistDao.updateForProfile(targetProfileId, playlist)
                            }
                            playlists++
                        }
                    }
                }

                result = Result(
                    historyItems,
                    progressItems,
                    playlists,
                    playlistItems,
                    subscriptions,
                    if (selection.importSettings) compatibleSettings.size else 0,
                    if (selection.importSponsorBlock) sponsorBlockSettings.size else 0,
                    skippedItems,
                    channelGroups,
                    searchHistoryItems
                )
            }
        } catch (error: Throwable) {
            if (sponsorBlockRollback != null) {
                try {
                    settingsMigration.rollback(sponsorBlockRollback)
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
            }
            if (settingsRollback != null) {
                try {
                    settingsMigration.rollback(settingsRollback)
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
            }
            throw error
        }
        result
    }

    private fun openSource(databasePath: Path): SQLiteDatabase = SQLiteDatabase.openDatabase(
        databasePath.toString(),
        null,
        SQLiteDatabase.OPEN_READONLY
    )

    private fun inspectSchema(source: SQLiteDatabase): SourceSchema {
        val streamColumns = source.columnsOf(STREAM_TABLE)
        val hasStreams = streamColumns.containsAll(REQUIRED_STREAM_COLUMNS)
        val historyColumns = source.columnsOf(HISTORY_TABLE)
        val stateColumns = source.columnsOf(STATE_TABLE)
        val playlistColumns = source.columnsOf(PLAYLIST_TABLE)
        val joinColumns = source.columnsOf(PLAYLIST_JOIN_TABLE)
        val subscriptionColumns = source.columnsOf(SUBSCRIPTION_TABLE)
        val schema = SourceSchema(
            hasHistory = hasStreams && historyColumns.containsAll(REQUIRED_HISTORY_COLUMNS),
            hasSearchHistory = source.columnsOf(SEARCH_HISTORY_TABLE).containsAll(REQUIRED_SEARCH_HISTORY_COLUMNS),
            hasProgress = hasStreams && stateColumns.containsAll(REQUIRED_STATE_COLUMNS),
            hasPlaylists = hasStreams &&
                playlistColumns.containsAll(REQUIRED_PLAYLIST_COLUMNS) &&
                joinColumns.containsAll(REQUIRED_PLAYLIST_JOIN_COLUMNS),
            hasSubscriptions = subscriptionColumns.containsAll(
                REQUIRED_SUBSCRIPTION_COLUMNS
            ),
            hasGroups = source.columnsOf(GROUP_TABLE).containsAll(setOf("uid", "name", "icon_id", "sort_order")),
            hasGroupMemberships = source.columnsOf(GROUP_JOIN_TABLE).containsAll(setOf("group_id", "subscription_id")),
            playlistColumns = playlistColumns,
            isPipePipe = source.tableExists(PIPEPIPE_SPONSORBLOCK_WHITELIST_TABLE) ||
                source.userVersion() >= PIPEPIPE_DATABASE_VERSION_FLOOR
        )
        if (!schema.hasHistory && !schema.hasSearchHistory && !schema.hasProgress && !schema.hasPlaylists &&
            !schema.hasSubscriptions && !schema.hasGroups
        ) {
            throw UnsupportedSourceException(
                "The source database does not contain compatible migration data"
            )
        }
        return schema
    }

    private fun readStreams(source: SQLiteDatabase): Map<Long, StreamEntity> {
        val result = mutableMapOf<Long, StreamEntity>()
        source.rawQuery("SELECT * FROM $STREAM_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val uid = cursor.long("uid") ?: continue
                val serviceId = cursor.long("service_id")?.toInt() ?: continue
                val url = cursor.string("url") ?: continue
                val streamType = cursor.string("stream_type")
                    ?.let { runCatching { StreamType.valueOf(it) }.getOrNull() }
                    ?: continue
                result[uid] = StreamEntity(
                    serviceId = serviceId,
                    url = url,
                    title = cursor.string("title").orEmpty(),
                    streamType = streamType,
                    duration = cursor.long("duration") ?: -1,
                    uploader = cursor.string("uploader").orEmpty(),
                    uploaderUrl = cursor.string("uploader_url"),
                    thumbnailUrl = cursor.string("thumbnail_url"),
                    viewCount = cursor.long("view_count"),
                    textualUploadDate = cursor.string("textual_upload_date"),
                    uploadDate = cursor.long("upload_date")?.toOffsetDateTime(),
                    isUploadDateApproximation = cursor.long("is_upload_date_approximation")
                        ?.let { it != 0L },
                    uploaderAvatarUrl = cursor.string("uploader_avatar_url"),
                    requiresMembership = cursor.long("requires_membership") == 1L
                )
            }
        }
        return result
    }

    private fun uniquePlaylistName(sourceName: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(sourceName)) {
            return sourceName
        }
        var suffix = 1
        while (true) {
            val candidate = if (suffix == 1) {
                "$sourceName (Imported)"
            } else {
                "$sourceName (Imported $suffix)"
            }
            if (usedNames.add(candidate)) {
                return candidate
            }
            suffix++
        }
    }

    private fun SQLiteDatabase.columnsOf(table: String): Set<String> {
        if (!tableExists(table)) {
            return emptySet()
        }
        return rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }
    }

    private fun SQLiteDatabase.tableExists(table: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(table)
    ).use { it.moveToFirst() }

    private fun SQLiteDatabase.countRows(table: String): Int = rawQuery(
        "SELECT COUNT(*) FROM `$table`",
        null
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            0
        } else {
            cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun SQLiteDatabase.userVersion(): Int = rawQuery(
        "PRAGMA user_version",
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }

    private fun Cursor.notificationMode(): Int? {
        val index = getColumnIndex(SubscriptionEntity.SUBSCRIPTION_NOTIFICATION_MODE)
        if (index < 0 || getType(index) != Cursor.FIELD_TYPE_INTEGER) {
            return null
        }
        return when (getLong(index)) {
            NotificationMode.DISABLED.toLong() -> NotificationMode.DISABLED
            NotificationMode.ENABLED.toLong() -> NotificationMode.ENABLED
            else -> null
        }
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(this),
        ZoneOffset.UTC
    )

    private data class SourceSchema(
        val hasHistory: Boolean,
        val hasSearchHistory: Boolean,
        val hasProgress: Boolean,
        val hasPlaylists: Boolean,
        val hasSubscriptions: Boolean,
        val hasGroups: Boolean,
        val hasGroupMemberships: Boolean,
        val playlistColumns: Set<String>,
        val isPipePipe: Boolean
    ) {
        val usesAlphabeticalPlaylistOrder: Boolean
            get() = isPipePipe
    }

    companion object {
        private const val STREAM_TABLE = "streams"
        private const val HISTORY_TABLE = "stream_history"
        private const val SEARCH_HISTORY_TABLE = "search_history"
        private const val STATE_TABLE = "stream_state"
        private const val PLAYLIST_TABLE = "playlists"
        private const val PLAYLIST_JOIN_TABLE = "playlist_stream_join"
        private const val GROUP_TABLE = "feed_group"
        private const val GROUP_JOIN_TABLE = "feed_group_subscription_join"
        private const val SUBSCRIPTION_TABLE = "subscriptions"
        private const val PIPEPIPE_SPONSORBLOCK_WHITELIST_TABLE = "sponsorblock_whitelist"
        private const val PIPEPIPE_DATABASE_VERSION_FLOOR = 900

        private val REQUIRED_STREAM_COLUMNS = setOf(
            "uid",
            "service_id",
            "url",
            "title",
            "stream_type",
            "duration",
            "uploader"
        )
        private val REQUIRED_SEARCH_HISTORY_COLUMNS = setOf("creation_date", "service_id", "search")
        private val REQUIRED_HISTORY_COLUMNS = setOf(
            "stream_id",
            "access_date",
            "repeat_count"
        )
        private val REQUIRED_STATE_COLUMNS = setOf("stream_id", "progress_time")
        private val REQUIRED_PLAYLIST_COLUMNS = setOf("uid", "name")
        private val REQUIRED_PLAYLIST_JOIN_COLUMNS = setOf(
            "playlist_id",
            "stream_id",
            "join_index"
        )
        private val REQUIRED_SUBSCRIPTION_COLUMNS = setOf(
            "service_id",
            "url",
            "name"
        )
    }
}
