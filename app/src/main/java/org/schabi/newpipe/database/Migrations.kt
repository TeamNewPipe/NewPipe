/*
 * SPDX-FileCopyrightText: 2018-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    val MIGRATION_9_10 = object : Migration(DB_VER_9, DB_VER_10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add folder table and folder_id column to playlists
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_folders` (" +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL DEFAULT 0)"
            )

            // Add nullable folder_id column to playlists
            db.execSQL(
                "ALTER TABLE `playlists` ADD COLUMN `folder_id` INTEGER"
            )
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10
    )
}
