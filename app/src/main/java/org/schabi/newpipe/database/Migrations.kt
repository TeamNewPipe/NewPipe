/*
 * SPDX-FileCopyrightText: 2018-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import android.util.Log
import androidx.room.migration.Migration
import org.schabi.newpipe.MainActivity

object Migrations {

    // /////////////////////////////////////////////////////////////////////// //
    //  Test new migrations manually by importing a database from daily usage  //
    //  and checking if the migration works (Use the Database Inspector        //
    //  https://developer.android.com/studio/inspect/database).                //
    //  If you add a migration point it out in the pull request, so that       //
    //  others remember to test it themselves.                                 //
    // /////////////////////////////////////////////////////////////////////// //

    const val DB_VER_1 = 1
    const val DB_VER_2 = 2
    const val DB_VER_3 = 3
    const val DB_VER_4 = 4
    const val DB_VER_5 = 5
    const val DB_VER_6 = 6
    const val DB_VER_7 = 7
    const val DB_VER_8 = 8
    const val DB_VER_9 = 9
    const val DB_VER_10 = 10
    const val DB_VER_11 = 11
    const val DB_VER_12 = 12
    const val DB_VER_13 = 13
    const val DB_VER_14 = 14
    const val DB_VER_15 = 15
    const val DB_VER_16 = 16
    const val DB_VER_17 = 17
    const val DB_VER_18 = 18
    const val DB_VER_19 = 19
    const val DB_VER_20 = 20
    const val DB_VER_21 = 21
    const val DB_VER_22 = 22
    const val DB_VER_23 = 23
    const val DB_VER_24 = 24
    const val DB_VER_25 = 25
    const val DB_VER_26 = 26
    const val DB_VER_27 = 27
    const val DB_VER_28 = 28
    const val DB_VER_29 = 29
    const val DB_VER_30 = 30
    const val DB_VER_31 = 31
    const val DB_VER_32 = 32
    const val DB_VER_33 = 33
    const val DB_VER_34 = 34
    const val DB_VER_CURRENT = DB_VER_34

    private val TAG = Migrations::class.java.getName()
    private val isDebug = MainActivity.DEBUG

    val MIGRATION_1_2 = Migration(DB_VER_1, DB_VER_2) { db ->
        if (isDebug) {
            Log.d(TAG, "Start migrating database")
        }

        /*
         * Unfortunately these queries must be hardcoded due to the possibility of
         * schema and names changing at a later date, thus invalidating the older migration
         * scripts if they are not hardcoded.
         * */

        // Not much we can do about this, since room doesn't create tables before migration.
        // It's either this or blasting the entire database anew.
        db.execSQL(
            "CREATE  INDEX `index_search_history_search` " +
                "ON `search_history` (`search`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `streams` " +
                "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`service_id` INTEGER NOT NULL, `url` TEXT, `title` TEXT, " +
                "`stream_type` TEXT, `duration` INTEGER, `uploader` TEXT, " +
                "`thumbnail_url` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX `index_streams_service_id_url` " +
                "ON `streams` (`service_id`, `url`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stream_history` " +
                "(`stream_id` INTEGER NOT NULL, `access_date` INTEGER NOT NULL, " +
                "`repeat_count` INTEGER NOT NULL, PRIMARY KEY(`stream_id`, `access_date`), " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE  INDEX `index_stream_history_stream_id` " +
                "ON `stream_history` (`stream_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stream_state` " +
                "(`stream_id` INTEGER NOT NULL, `progress_time` INTEGER NOT NULL, " +
                "PRIMARY KEY(`stream_id`), FOREIGN KEY(`stream_id`) " +
                "REFERENCES `streams`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` " +
                "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, `thumbnail_url` TEXT)"
        )
        db.execSQL("CREATE  INDEX `index_playlists_name` ON `playlists` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_stream_join` " +
                "(`playlist_id` INTEGER NOT NULL, `stream_id` INTEGER NOT NULL, " +
                "`join_index` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `join_index`), " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX " +
                "`index_playlist_stream_join_playlist_id_join_index` " +
                "ON `playlist_stream_join` (`playlist_id`, `join_index`)"
        )
        db.execSQL(
            "CREATE  INDEX `index_playlist_stream_join_stream_id` " +
                "ON `playlist_stream_join` (`stream_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_playlists` " +
                "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, " +
                "`thumbnail_url` TEXT, `uploader` TEXT, `stream_count` INTEGER)"
        )
        db.execSQL(
            "CREATE  INDEX `index_remote_playlists_name` " +
                "ON `remote_playlists` (`name`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` " +
                "ON `remote_playlists` (`service_id`, `url`)"
        )

        // Populate streams table with existing entries in watch history
        // Latest data first, thus ignoring older entries with the same indices
        db.execSQL(
            "INSERT OR IGNORE INTO streams (service_id, url, title, " +
                "stream_type, duration, uploader, thumbnail_url) " +

                "SELECT service_id, url, title, 'VIDEO_STREAM', duration, " +
                "uploader, thumbnail_url " +

                "FROM watch_history " +
                "ORDER BY creation_date DESC"
        )

        // Once the streams have PKs, join them with the normalized history table
        // and populate it with the remaining data from watch history
        db.execSQL(
            "INSERT INTO stream_history (stream_id, access_date, repeat_count)" +
                "SELECT uid, creation_date, 1 " +
                "FROM watch_history INNER JOIN streams " +
                "ON watch_history.service_id == streams.service_id " +
                "AND watch_history.url == streams.url " +
                "ORDER BY creation_date DESC"
        )

        db.execSQL("DROP TABLE IF EXISTS watch_history")

        if (isDebug) {
            Log.d(TAG, "Stop migrating database")
        }
    }

    val MIGRATION_2_3 = Migration(DB_VER_2, DB_VER_3) { db ->
        // Add NOT NULLs and new fields
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS streams_new " +
                "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "service_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, " +
                "stream_type TEXT NOT NULL, duration INTEGER NOT NULL, " +
                "uploader TEXT NOT NULL, thumbnail_url TEXT, view_count INTEGER, " +
                "textual_upload_date TEXT, upload_date INTEGER, " +
                "is_upload_date_approximation INTEGER)"
        )

        db.execSQL(
            "INSERT INTO streams_new (uid, service_id, url, title, stream_type, " +
                "duration, uploader, thumbnail_url, view_count, textual_upload_date, " +
                "upload_date, is_upload_date_approximation) " +

                "SELECT uid, service_id, url, ifnull(title, ''), " +
                "ifnull(stream_type, 'VIDEO_STREAM'), ifnull(duration, 0), " +
                "ifnull(uploader, ''), ifnull(thumbnail_url, ''), NULL, NULL, NULL, NULL " +

                "FROM streams WHERE url IS NOT NULL"
        )

        db.execSQL("DROP TABLE streams")
        db.execSQL("ALTER TABLE streams_new RENAME TO streams")
        db.execSQL(
            "CREATE UNIQUE INDEX index_streams_service_id_url " +
                "ON streams (service_id, url)"
        )

        // Tables for feed feature
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS feed " +
                "(stream_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, " +
                "PRIMARY KEY(stream_id, subscription_id), " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
        db.execSQL("CREATE INDEX index_feed_subscription_id ON feed (subscription_id)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS feed_group " +
                "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX index_feed_group_sort_order ON feed_group (sort_order)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS feed_group_subscription_join " +
                "(group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, " +
                "PRIMARY KEY(group_id, subscription_id), " +
                "FOREIGN KEY(group_id) REFERENCES feed_group(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
        db.execSQL(
            "CREATE INDEX index_feed_group_subscription_join_subscription_id " +
                "ON feed_group_subscription_join (subscription_id)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS feed_last_updated " +
                "(subscription_id INTEGER NOT NULL, last_updated INTEGER, " +
                "PRIMARY KEY(subscription_id), " +
                "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
    }

    val MIGRATION_3_4 = Migration(DB_VER_3, DB_VER_4) { db ->
        db.execSQL("ALTER TABLE streams ADD COLUMN uploader_url TEXT")
    }

    val MIGRATION_4_5 = Migration(DB_VER_4, DB_VER_5) { db ->
        db.execSQL(
            "ALTER TABLE `subscriptions` ADD COLUMN `notification_mode` " +
                "INTEGER NOT NULL DEFAULT 0"
        )
    }

    val MIGRATION_5_6 = Migration(DB_VER_5, DB_VER_6) { db ->
        db.execSQL(
            "ALTER TABLE `playlists` ADD COLUMN `is_thumbnail_permanent` " +
                "INTEGER NOT NULL DEFAULT 0"
        )
    }

    val MIGRATION_6_7 = Migration(DB_VER_6, DB_VER_7) { db ->
        // Create a new column thumbnail_stream_id
        db.execSQL(
            "ALTER TABLE `playlists` ADD COLUMN `thumbnail_stream_id` " +
                "INTEGER NOT NULL DEFAULT -1"
        )

        // Migrate the thumbnail_url to the thumbnail_stream_id
        db.execSQL(
            "UPDATE playlists SET thumbnail_stream_id = (" +
                " SELECT CASE WHEN COUNT(*) != 0 then stream_uid ELSE -1 END" +
                " FROM (" +
                " SELECT p.uid AS playlist_uid, s.uid AS stream_uid" +
                " FROM playlists p" +
                " LEFT JOIN playlist_stream_join ps ON p.uid = ps.playlist_id" +
                " LEFT JOIN streams s ON s.uid = ps.stream_id" +
                " WHERE s.thumbnail_url = p.thumbnail_url) AS temporary_table" +
                " WHERE playlist_uid = playlists.uid)"
        )

        // Remove the thumbnail_url field in the playlist table
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists_new`" +
                "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT, " +
                "is_thumbnail_permanent INTEGER NOT NULL, " +
                "thumbnail_stream_id INTEGER NOT NULL)"
        )

        db.execSQL(
            "INSERT INTO playlists_new" +
                " SELECT uid, name, is_thumbnail_permanent, thumbnail_stream_id " +
                " FROM playlists"
        )

        db.execSQL("DROP TABLE playlists")
        db.execSQL("ALTER TABLE playlists_new RENAME TO playlists")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_playlists_name` ON `playlists` (`name`)"
        )
    }

    val MIGRATION_7_8 = Migration(DB_VER_7, DB_VER_8) { db ->
        db.execSQL(
            "DELETE FROM search_history WHERE id NOT IN (SELECT id FROM (SELECT " +
                "MIN(id) as id FROM search_history GROUP BY trim(search), service_id ) tmp)"
        )
        db.execSQL("UPDATE search_history SET search = trim(search)")
    }

    val MIGRATION_8_9 = Migration(DB_VER_8, DB_VER_9) { db ->
        try {
            db.beginTransaction()

            // Update playlists.
            // Create a temp table to initialize display_index.
            db.execSQL(
                "CREATE TABLE `playlists_tmp` " +
                    "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT, `is_thumbnail_permanent` INTEGER NOT NULL, " +
                    "`thumbnail_stream_id` INTEGER NOT NULL, " +
                    "`display_index` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `playlists_tmp` " +
                    "(`uid`, `name`, `is_thumbnail_permanent`, `thumbnail_stream_id`, " +
                    "`display_index`) " +
                    "SELECT `uid`, `name`, `is_thumbnail_permanent`, `thumbnail_stream_id`, " +
                    "-1 " +
                    "FROM `playlists`"
            )

            // Replace the old table, note that this also removes the index on the name which
            // we don't need anymore.
            db.execSQL("DROP TABLE `playlists`")
            db.execSQL("ALTER TABLE `playlists_tmp` RENAME TO `playlists`")

            // Update remote_playlists.
            // Create a temp table to initialize display_index.
            db.execSQL(
                "CREATE TABLE `remote_playlists_tmp` " +
                    "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, " +
                    "`thumbnail_url` TEXT, `uploader` TEXT, " +
                    "`display_index` INTEGER NOT NULL," +
                    "`stream_count` INTEGER)"
            )
            db.execSQL(
                "INSERT INTO `remote_playlists_tmp` (`uid`, `service_id`, " +
                    "`name`, `url`, `thumbnail_url`, `uploader`, `display_index`, " +
                    "`stream_count`)" +
                    "SELECT `uid`, `service_id`, `name`, `url`, `thumbnail_url`, `uploader`, " +
                    "-1, `stream_count` FROM `remote_playlists`"
            )

            // Replace the old table, note that this also removes the index on the name which
            // we don't need anymore.
            db.execSQL("DROP TABLE `remote_playlists`")
            db.execSQL("ALTER TABLE `remote_playlists_tmp` RENAME TO `remote_playlists`")

            // Create index on the new table.
            db.execSQL(
                "CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` " +
                    "ON `remote_playlists` (`service_id`, `url`)"
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    val MIGRATION_9_10 = Migration(DB_VER_9, DB_VER_10) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription_sync_changes` " +
                "(`origin_peer_id` TEXT NOT NULL, `origin_revision` INTEGER NOT NULL, " +
                "`lamport_version` INTEGER NOT NULL, `record_id` TEXT NOT NULL, " +
                "`change_type` TEXT NOT NULL, `service_id` INTEGER NOT NULL, " +
                "`url` TEXT NOT NULL, `name` TEXT, `avatar_url` TEXT, " +
                "`subscriber_count` INTEGER, `description` TEXT, " +
                "PRIMARY KEY(`origin_peer_id`, `origin_revision`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_subscription_sync_changes_record_id` " +
                "ON `subscription_sync_changes` (`record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_subscription_sync_changes_lamport_version_origin_peer_id_" +
                "origin_revision` ON `subscription_sync_changes` " +
                "(`lamport_version`, `origin_peer_id`, `origin_revision`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription_sync_records` " +
                "(`record_id` TEXT NOT NULL, `service_id` INTEGER NOT NULL, " +
                "`url` TEXT NOT NULL, `lamport_version` INTEGER NOT NULL, " +
                "`origin_peer_id` TEXT NOT NULL, `origin_revision` INTEGER NOT NULL, " +
                "`is_deleted` INTEGER NOT NULL, PRIMARY KEY(`record_id`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_subscription_sync_records_service_id_url` " +
                "ON `subscription_sync_records` (`service_id`, `url`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription_sync_origin_state` " +
                "(`origin_peer_id` TEXT NOT NULL, `contiguous_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`origin_peer_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscription_sync_peer_state` " +
                "(`peer_id` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`acknowledged_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`peer_id`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_subscription_sync_peer_state_origin_peer_id` " +
                "ON `subscription_sync_peer_state` (`origin_peer_id`)"
        )
    }

    val MIGRATION_10_11 = Migration(DB_VER_10, DB_VER_11) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_sync_changes` " +
                "(`origin_peer_id` TEXT NOT NULL, `origin_revision` INTEGER NOT NULL, " +
                "`lamport_version` INTEGER NOT NULL, `record_id` TEXT NOT NULL, " +
                "`record_type` TEXT NOT NULL, `parent_record_id` TEXT, " +
                "`change_type` TEXT NOT NULL, `record_json` TEXT, " +
                "PRIMARY KEY(`origin_peer_id`, `origin_revision`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_sync_changes_record_id` " +
                "ON `playlist_sync_changes` (`record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_sync_changes_parent_record_id` " +
                "ON `playlist_sync_changes` (`parent_record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_playlist_sync_changes_lamport_version_origin_peer_id_" +
                "origin_revision` ON `playlist_sync_changes` " +
                "(`lamport_version`, `origin_peer_id`, `origin_revision`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_sync_records` " +
                "(`record_id` TEXT NOT NULL, `record_type` TEXT NOT NULL, " +
                "`parent_record_id` TEXT, `lamport_version` INTEGER NOT NULL, " +
                "`origin_peer_id` TEXT NOT NULL, `origin_revision` INTEGER NOT NULL, " +
                "`is_deleted` INTEGER NOT NULL, `record_json` TEXT, " +
                "PRIMARY KEY(`record_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_sync_records_parent_record_id` " +
                "ON `playlist_sync_records` (`parent_record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_sync_records_record_type` " +
                "ON `playlist_sync_records` (`record_type`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_sync_origin_state` " +
                "(`origin_peer_id` TEXT NOT NULL, `contiguous_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`origin_peer_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_sync_peer_state` " +
                "(`peer_id` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`acknowledged_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`peer_id`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_playlist_sync_peer_state_origin_peer_id` " +
                "ON `playlist_sync_peer_state` (`origin_peer_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_sync_local_map` " +
                "(`playlist_record_id` TEXT NOT NULL, `playlist_uid` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlist_record_id`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_playlist_sync_local_map_playlist_uid` " +
                "ON `playlist_sync_local_map` (`playlist_uid`)"
        )
    }

    val MIGRATION_11_12 = Migration(DB_VER_11, DB_VER_12) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_sync_changes` " +
                "(`category` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`origin_revision` INTEGER NOT NULL, `lamport_version` INTEGER NOT NULL, " +
                "`record_id` TEXT NOT NULL, `record_type` TEXT NOT NULL, " +
                "`change_type` TEXT NOT NULL, `record_json` TEXT, " +
                "PRIMARY KEY(`category`, `origin_peer_id`, `origin_revision`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_history_sync_changes_category_record_id` " +
                "ON `history_sync_changes` (`category`, `record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_history_sync_changes_category_lamport_version_origin_peer_id_" +
                "origin_revision` ON `history_sync_changes` " +
                "(`category`, `lamport_version`, `origin_peer_id`, `origin_revision`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_sync_records` " +
                "(`category` TEXT NOT NULL, `record_id` TEXT NOT NULL, " +
                "`record_type` TEXT NOT NULL, `lamport_version` INTEGER NOT NULL, " +
                "`origin_peer_id` TEXT NOT NULL, `origin_revision` INTEGER NOT NULL, " +
                "`is_deleted` INTEGER NOT NULL, `record_json` TEXT, " +
                "PRIMARY KEY(`category`, `record_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_history_sync_records_category_record_type` " +
                "ON `history_sync_records` (`category`, `record_type`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_sync_origin_state` " +
                "(`category` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`contiguous_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_sync_peer_state` " +
                "(`category` TEXT NOT NULL, `peer_id` TEXT NOT NULL, " +
                "`origin_peer_id` TEXT NOT NULL, `acknowledged_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`, `peer_id`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_history_sync_peer_state_category_origin_peer_id` " +
                "ON `history_sync_peer_state` (`category`, `origin_peer_id`)"
        )
    }

    val MIGRATION_12_13 = Migration(DB_VER_12, DB_VER_13) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_changes` " +
                "(`category` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`origin_revision` INTEGER NOT NULL, `lamport_version` INTEGER NOT NULL, " +
                "`record_id` TEXT NOT NULL, `record_type` TEXT NOT NULL, " +
                "`parent_record_id` TEXT, `change_type` TEXT NOT NULL, " +
                "`record_json` TEXT NOT NULL, " +
                "PRIMARY KEY(`category`, `origin_peer_id`, `origin_revision`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_changes_category_record_id` " +
                "ON `structured_preference_sync_changes` (`category`, `record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_changes_category_parent_record_id` " +
                "ON `structured_preference_sync_changes` (`category`, `parent_record_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_changes_category_lamport_version_" +
                "origin_peer_id_origin_revision` ON `structured_preference_sync_changes` " +
                "(`category`, `lamport_version`, `origin_peer_id`, `origin_revision`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_records` " +
                "(`category` TEXT NOT NULL, `record_id` TEXT NOT NULL, " +
                "`record_type` TEXT NOT NULL, `parent_record_id` TEXT, " +
                "`lamport_version` INTEGER NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`origin_revision` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, " +
                "`record_json` TEXT NOT NULL, PRIMARY KEY(`category`, `record_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_records_category_record_type` " +
                "ON `structured_preference_sync_records` (`category`, `record_type`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_records_category_parent_record_id` " +
                "ON `structured_preference_sync_records` (`category`, `parent_record_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_origin_state` " +
                "(`category` TEXT NOT NULL, `origin_peer_id` TEXT NOT NULL, " +
                "`contiguous_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_peer_state` " +
                "(`category` TEXT NOT NULL, `peer_id` TEXT NOT NULL, " +
                "`origin_peer_id` TEXT NOT NULL, `acknowledged_revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`, `peer_id`, `origin_peer_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_peer_state_category_origin_peer_id` " +
                "ON `structured_preference_sync_peer_state` " +
                "(`category`, `origin_peer_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_feed_group_map` " +
                "(`group_record_id` TEXT NOT NULL, `group_uid` INTEGER NOT NULL, " +
                "PRIMARY KEY(`group_record_id`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_structured_preference_sync_feed_group_map_group_uid` " +
                "ON `structured_preference_sync_feed_group_map` (`group_uid`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `structured_preference_sync_local_state` " +
                "(`category` TEXT NOT NULL, `snapshot_hash` TEXT NOT NULL, " +
                "PRIMARY KEY(`category`))"
        )
    }

    val MIGRATION_13_14 = Migration(DB_VER_13, DB_VER_14) { db ->
        db.execSQL(
            "ALTER TABLE `subscriptions` ADD COLUMN `youtube_mode_mask` " +
                "INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_changes` ADD COLUMN `youtube_mode_mask` INTEGER"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_records` ADD COLUMN `youtube_mode_mask` " +
                "INTEGER NOT NULL DEFAULT 1"
        )
    }

    val MIGRATION_14_15 = Migration(DB_VER_14, DB_VER_15) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_new` " +
                "(`stream_id` INTEGER NOT NULL, `subscription_id` INTEGER NOT NULL, " +
                "`youtube_mode_mask` INTEGER NOT NULL DEFAULT 1, " +
                "PRIMARY KEY(`stream_id`, `subscription_id`, `youtube_mode_mask`), " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, " +
                "FOREIGN KEY(`subscription_id`) REFERENCES `subscriptions`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
        db.execSQL(
            "INSERT INTO `feed_new` (`stream_id`, `subscription_id`, `youtube_mode_mask`) " +
                "SELECT f.`stream_id`, f.`subscription_id`, 1 FROM `feed` f " +
                "INNER JOIN `subscriptions` s ON s.`uid` = f.`subscription_id` " +
                "WHERE s.`service_id` <> 0 OR (s.`youtube_mode_mask` & 1) <> 0"
        )
        db.execSQL(
            "INSERT INTO `feed_new` (`stream_id`, `subscription_id`, `youtube_mode_mask`) " +
                "SELECT f.`stream_id`, f.`subscription_id`, 2 FROM `feed` f " +
                "INNER JOIN `subscriptions` s ON s.`uid` = f.`subscription_id` " +
                "WHERE s.`service_id` = 0 AND (s.`youtube_mode_mask` & 2) <> 0"
        )
        db.execSQL("DROP TABLE `feed`")
        db.execSQL("ALTER TABLE `feed_new` RENAME TO `feed`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feed_subscription_id` " +
                "ON `feed` (`subscription_id`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_last_updated_new` " +
                "(`subscription_id` INTEGER NOT NULL, " +
                "`youtube_mode_mask` INTEGER NOT NULL DEFAULT 1, `last_updated` INTEGER, " +
                "PRIMARY KEY(`subscription_id`, `youtube_mode_mask`), " +
                "FOREIGN KEY(`subscription_id`) REFERENCES `subscriptions`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)"
        )
        db.execSQL(
            "INSERT INTO `feed_last_updated_new` " +
                "(`subscription_id`, `youtube_mode_mask`, `last_updated`) " +
                "SELECT lu.`subscription_id`, 1, lu.`last_updated` " +
                "FROM `feed_last_updated` lu " +
                "INNER JOIN `subscriptions` s ON s.`uid` = lu.`subscription_id` " +
                "WHERE s.`service_id` <> 0 OR (s.`youtube_mode_mask` & 1) <> 0"
        )
        db.execSQL(
            "INSERT INTO `feed_last_updated_new` " +
                "(`subscription_id`, `youtube_mode_mask`, `last_updated`) " +
                "SELECT lu.`subscription_id`, 2, lu.`last_updated` " +
                "FROM `feed_last_updated` lu " +
                "INNER JOIN `subscriptions` s ON s.`uid` = lu.`subscription_id` " +
                "WHERE s.`service_id` = 0 AND (s.`youtube_mode_mask` & 2) <> 0"
        )
        db.execSQL("DROP TABLE `feed_last_updated`")
        db.execSQL(
            "ALTER TABLE `feed_last_updated_new` RENAME TO `feed_last_updated`"
        )
    }

    val MIGRATION_15_16 = Migration(DB_VER_15, DB_VER_16) { db ->
        db.execSQL("ALTER TABLE `streams` ADD COLUMN `uploader_avatar_url` TEXT")
    }

    val MIGRATION_16_17 = Migration(DB_VER_16, DB_VER_17) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learning_notes` (" +
                "`note_id` TEXT NOT NULL, `stream_id` INTEGER NOT NULL, " +
                "`timestamp_ms` INTEGER NOT NULL, `note_text` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`note_id`), " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_notes_stream_id_timestamp_ms` " +
                "ON `learning_notes` (`stream_id`, `timestamp_ms`)"
        )
    }

    val MIGRATION_17_18 = Migration(DB_VER_17, DB_VER_18) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learning_sessions` (" +
                "`session_id` TEXT NOT NULL, `stream_id` INTEGER NOT NULL, " +
                "`started_at` INTEGER NOT NULL, `ended_at` INTEGER NOT NULL, " +
                "`watched_duration_ms` INTEGER NOT NULL, `local_date` TEXT NOT NULL, " +
                "`background_playback` INTEGER NOT NULL, PRIMARY KEY(`session_id`), " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_sessions_stream_id_started_at` " +
                "ON `learning_sessions` (`stream_id`, `started_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_sessions_local_date` " +
                "ON `learning_sessions` (`local_date`)"
        )
    }

    val MIGRATION_18_19 = Migration(DB_VER_18, DB_VER_19) { db ->
        db.execSQL(
            "ALTER TABLE streams ADD COLUMN requires_membership " +
                "INTEGER NOT NULL DEFAULT 0"
        )
    }

    val MIGRATION_19_20 = Migration(DB_VER_19, DB_VER_20) { db ->
        db.execSQL(
            "ALTER TABLE streams ADD COLUMN source_type " +
                "TEXT NOT NULL DEFAULT 'REMOTE'"
        )
        db.execSQL("ALTER TABLE streams ADD COLUMN mime_type TEXT")
        db.execSQL("ALTER TABLE streams ADD COLUMN local_media_id INTEGER")
        db.execSQL("ALTER TABLE streams ADD COLUMN local_album TEXT")
        db.execSQL("ALTER TABLE streams ADD COLUMN local_folder TEXT")
    }

    val MIGRATION_20_21 = Migration(DB_VER_20, DB_VER_21) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learning_content_sources` (" +
                "`source_id` TEXT NOT NULL, `source_type` TEXT NOT NULL, " +
                "`local_playlist_id` INTEGER, `service_id` INTEGER, `url` TEXT, " +
                "`title` TEXT, `thumbnail_url` TEXT, `created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`source_id`), FOREIGN KEY(`local_playlist_id`) " +
                "REFERENCES `playlists`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_content_sources_local_playlist_id` " +
                "ON `learning_content_sources` (`local_playlist_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_content_sources_source_type` " +
                "ON `learning_content_sources` (`source_type`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_content_sources_service_id_url` " +
                "ON `learning_content_sources` (`service_id`, `url`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learning_content_streams` (" +
                "`source_id` TEXT NOT NULL, `stream_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`source_id`, `stream_id`), " +
                "FOREIGN KEY(`source_id`) REFERENCES `learning_content_sources`(`source_id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE, " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_content_streams_stream_id` " +
                "ON `learning_content_streams` (`stream_id`)"
        )
        db.execSQL(
            "ALTER TABLE `learning_sessions` ADD COLUMN `is_designated` " +
                "INTEGER NOT NULL DEFAULT 0"
        )

        // Existing notes are an unambiguous indication that a stream was used for learning.
        db.execSQL(
            "INSERT OR IGNORE INTO learning_content_sources " +
                "(source_id, source_type, service_id, url, title, thumbnail_url, created_at) " +
                "SELECT 'stream:' || streams.service_id || ':' || streams.url, 'STREAM', " +
                "streams.service_id, streams.url, streams.title, streams.thumbnail_url, " +
                "CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                "FROM streams INNER JOIN learning_notes ON streams.uid = learning_notes.stream_id " +
                "GROUP BY streams.uid"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO learning_content_streams (source_id, stream_id) " +
                "SELECT 'stream:' || streams.service_id || ':' || streams.url, streams.uid " +
                "FROM streams INNER JOIN learning_notes ON streams.uid = learning_notes.stream_id " +
                "GROUP BY streams.uid"
        )
        db.execSQL(
            "UPDATE learning_sessions SET is_designated = 1 WHERE stream_id IN " +
                "(SELECT stream_id FROM learning_content_streams)"
        )
    }

    val MIGRATION_21_22 = Migration(DB_VER_21, DB_VER_22) { db ->
        db.execSQL(
            "ALTER TABLE `subscriptions` ADD COLUMN `notification_keywords` " +
                "TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_changes` ADD COLUMN `notification_mode` INTEGER"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_changes` ADD COLUMN `notification_keywords` TEXT"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_records` ADD COLUMN `notification_mode` INTEGER"
        )
        db.execSQL(
            "ALTER TABLE `subscription_sync_records` ADD COLUMN `notification_keywords` TEXT"
        )
    }

    val MIGRATION_22_23 = Migration(DB_VER_22, DB_VER_23) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_search_feed` (" +
                "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `service_id` INTEGER NOT NULL, " +
                "`query` TEXT NOT NULL, `content_filter` TEXT NOT NULL, " +
                "`sort_filter` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, " +
                "`last_refresh` INTEGER)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_search_feed_sort_order` " +
                "ON `saved_search_feed` (`sort_order`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_saved_search_feed_service_id_query_content_filter_sort_filter` " +
                "ON `saved_search_feed` " +
                "(`service_id`, `query`, `content_filter`, `sort_filter`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_search_feed_stream` (" +
                "`feed_id` INTEGER NOT NULL, `stream_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`feed_id`, `stream_id`), " +
                "FOREIGN KEY(`feed_id`) REFERENCES `saved_search_feed`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE, " +
                "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_search_feed_stream_stream_id` " +
                "ON `saved_search_feed_stream` (`stream_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_saved_search_feed_stream_feed_id_position` " +
                "ON `saved_search_feed_stream` (`feed_id`, `position`)"
        )
    }

    val MIGRATION_23_24 = Migration(DB_VER_23, DB_VER_24) { db ->
        // Historical discovery times were never stored. Keep them older than newly found
        // videos and retain publication-date ordering as the tie breaker.
        db.execSQL("ALTER TABLE feed ADD COLUMN first_discovered_at INTEGER NOT NULL DEFAULT 0")
    }

    val MIGRATION_24_25 = Migration(DB_VER_24, DB_VER_25) { db ->
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"

        db.execSQL(
            "ALTER TABLE subscriptions ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL("DROP INDEX IF EXISTS index_subscriptions_service_id_url")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_subscriptions_profile_id_service_id_url " +
                "ON subscriptions (profile_id, service_id, url)"
        )

        db.execSQL(
            "ALTER TABLE feed_group ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL("DROP INDEX IF EXISTS index_feed_group_sort_order")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_feed_group_profile_id_sort_order " +
                "ON feed_group (profile_id, sort_order)"
        )
    }

    val MIGRATION_25_26 = Migration(DB_VER_25, DB_VER_26) { db ->
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS stream_history_new (" +
                "stream_id INTEGER NOT NULL, access_date INTEGER NOT NULL, " +
                "repeat_count INTEGER NOT NULL, profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId', " +
                "PRIMARY KEY(profile_id, stream_id, access_date), " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO stream_history_new " +
                "(stream_id, access_date, repeat_count, profile_id) " +
                "SELECT stream_id, access_date, repeat_count, '$defaultProfileId' " +
                "FROM stream_history"
        )
        db.execSQL("DROP TABLE stream_history")
        db.execSQL("ALTER TABLE stream_history_new RENAME TO stream_history")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stream_history_stream_id " +
                "ON stream_history (stream_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stream_history_profile_id_stream_id " +
                "ON stream_history (profile_id, stream_id)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS stream_state_new (" +
                "stream_id INTEGER NOT NULL, progress_time INTEGER NOT NULL, " +
                "profile_id TEXT NOT NULL DEFAULT '$defaultProfileId', " +
                "PRIMARY KEY(profile_id, stream_id), " +
                "FOREIGN KEY(stream_id) REFERENCES streams(uid) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO stream_state_new (stream_id, progress_time, profile_id) " +
                "SELECT stream_id, progress_time, '$defaultProfileId' FROM stream_state"
        )
        db.execSQL("DROP TABLE stream_state")
        db.execSQL("ALTER TABLE stream_state_new RENAME TO stream_state")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stream_state_stream_id " +
                "ON stream_state (stream_id)"
        )

        db.execSQL(
            "ALTER TABLE learning_sessions ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_learning_sessions_profile_id_local_date " +
                "ON learning_sessions (profile_id, local_date)"
        )
    }

    val MIGRATION_26_27 = Migration(DB_VER_26, DB_VER_27) { db ->
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"

        db.execSQL(
            "ALTER TABLE playlists ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_playlists_profile_id_display_index " +
                "ON playlists (profile_id, display_index)"
        )

        db.execSQL(
            "ALTER TABLE remote_playlists ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL("DROP INDEX IF EXISTS index_remote_playlists_service_id_url")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_remote_playlists_profile_id_service_id_url " +
                "ON remote_playlists (profile_id, service_id, url)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_remote_playlists_profile_id_display_index " +
                "ON remote_playlists (profile_id, display_index)"
        )

        db.execSQL(
            "ALTER TABLE saved_search_feed ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL("DROP INDEX IF EXISTS index_saved_search_feed_sort_order")
        db.execSQL(
            "DROP INDEX IF EXISTS " +
                "index_saved_search_feed_service_id_query_content_filter_sort_filter"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_saved_search_feed_profile_id_sort_order " +
                "ON saved_search_feed (profile_id, sort_order)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_saved_search_feed_profile_id_service_id_query_content_filter_sort_filter " +
                "ON saved_search_feed " +
                "(profile_id, service_id, query, content_filter, sort_filter)"
        )
    }

    val MIGRATION_27_28 = Migration(DB_VER_27, DB_VER_28) { db ->
        val defaultProfileId = "00000000-0000-0000-0000-000000000000"

        db.execSQL(
            "ALTER TABLE subscription_sync_changes ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "ALTER TABLE subscription_sync_records ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "DROP INDEX IF EXISTS index_subscription_sync_records_service_id_url"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "index_subscription_sync_records_profile_id_service_id_url " +
                "ON subscription_sync_records (profile_id, service_id, url)"
        )

        db.execSQL(
            "ALTER TABLE playlist_sync_changes ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "ALTER TABLE playlist_sync_records ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )

        db.execSQL(
            "ALTER TABLE history_sync_changes ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
        db.execSQL(
            "ALTER TABLE history_sync_records ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '$defaultProfileId'"
        )
    }

    val MIGRATION_28_29 = Migration(DB_VER_28, DB_VER_29) { db ->
        db.execSQL(
            "ALTER TABLE streams ADD COLUMN is_short_form_content " +
                "INTEGER NOT NULL DEFAULT 0"
        )
        // Preserve the classification of legacy YouTube Shorts that were already cached.
        // Other services keep the safe default and will populate the flag on the next refresh.
        db.execSQL(
            "UPDATE streams SET is_short_form_content = 1 " +
                "WHERE service_id = 0 AND url LIKE '%/shorts/%'"
        )
    }

    val MIGRATION_29_30 = Migration(DB_VER_29, DB_VER_30) { db ->
        db.execSQL(
            "ALTER TABLE search_history ADD COLUMN profile_id TEXT NOT NULL " +
                "DEFAULT '00000000-0000-0000-0000-000000000000'"
        )
        db.execSQL("DROP INDEX IF EXISTS index_search_history_search")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_search_history_profile_id_search " +
                "ON search_history (profile_id, search)"
        )
    }
    val MIGRATION_30_31 = Migration(DB_VER_30, DB_VER_31) { db ->
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN handle TEXT")
    }

    val MIGRATION_31_32 = Migration(DB_VER_31, DB_VER_32) { db ->
        // Unknown is deliberately NULL; do not infer provider categories from titles or URLs.
        db.execSQL("ALTER TABLE streams ADD COLUMN content_category TEXT")
    }

    val MIGRATION_32_33 = Migration(DB_VER_32, DB_VER_33) { db ->
        db.execSQL("ALTER TABLE history_sync_records ADD COLUMN stream_key TEXT")
        HistorySyncStreamKeyMigration.backfill(db)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_history_sync_records_category_profile_id_stream_key " +
                "ON history_sync_records (category, profile_id, stream_key)"
        )
    }

    val MIGRATION_33_34 = Migration(DB_VER_33, DB_VER_34) { db ->
        // Legacy dates cannot prove whether a delayed broadcast has started. Keep them unknown.
        db.execSQL("ALTER TABLE streams ADD COLUMN upcoming_status INTEGER")
    }
}
