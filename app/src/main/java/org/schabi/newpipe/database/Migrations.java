package org.schabi.newpipe.database;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.schabi.newpipe.MainActivity;

public final class Migrations {

    /////////////////////////////////////////////////////////////////////////////
    //  Test new migrations manually by importing a database from daily usage  //
    //  and checking if the migration works (Use the Database Inspector        //
    //  https://developer.android.com/studio/inspect/database).                //
    //  If you add a migration point it out in the pull request, so that       //
    //  others remember to test it themselves.                                 //
    /////////////////////////////////////////////////////////////////////////////

    public static final int DB_VER_1 = 1;
    public static final int DB_VER_2 = 2;
    public static final int DB_VER_3 = 3;
    public static final int DB_VER_4 = 4;
    public static final int DB_VER_5 = 5;
    public static final int DB_VER_6 = 6;
    public static final int DB_VER_7 = 7;
    public static final int DB_VER_8 = 8;
    public static final int DB_VER_9 = 9;

    private static final String TAG = Migrations.class.getName();
    public static final boolean DEBUG = MainActivity.DEBUG;

    public static final Migration MIGRATION_1_2 = new Migration(DB_VER_1, DB_VER_2) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            if (DEBUG) {
                Log.d(TAG, "Start migrating database");
            }
            /*
             * Unfortunately these queries must be hardcoded due to the possibility of
             * schema and names changing at a later date, thus invalidating the older migration
             * scripts if they are not hardcoded.
             * */

            // Not much we can do about this, since room doesn't create tables before migration.
            // It's either this or blasting the entire database anew.
            database.execSQL("CREATE  INDEX `index_search_history_search` "
                    + "ON `search_history` (`search`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `streams` "
                    + "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`service_id` INTEGER NOT NULL, `url` TEXT, `title` TEXT, "
                    + "`stream_type` TEXT, `duration` INTEGER, `uploader` TEXT, "
                    + "`thumbnail_url` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX `index_streams_service_id_url` "
                    + "ON `streams` (`service_id`, `url`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `stream_history` "
                    + "(`stream_id` INTEGER NOT NULL, `access_date` INTEGER NOT NULL, "
                    + "`repeat_count` INTEGER NOT NULL, PRIMARY KEY(`stream_id`, `access_date`), "
                    + "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("CREATE  INDEX `index_stream_history_stream_id` "
                    + "ON `stream_history` (`stream_id`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `stream_state` "
                    + "(`stream_id` INTEGER NOT NULL, `progress_time` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`stream_id`), FOREIGN KEY(`stream_id`) "
                    + "REFERENCES `streams`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` "
                    + "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT, `thumbnail_url` TEXT)");
            database.execSQL("CREATE  INDEX `index_playlists_name` ON `playlists` (`name`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_stream_join` "
                    + "(`playlist_id` INTEGER NOT NULL, `stream_id` INTEGER NOT NULL, "
                    + "`join_index` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `join_index`), "
                    + "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`uid`) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, "
                    + "FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
            database.execSQL("CREATE UNIQUE INDEX "
                    + "`index_playlist_stream_join_playlist_id_join_index` "
                    + "ON `playlist_stream_join` (`playlist_id`, `join_index`)");
            database.execSQL("CREATE  INDEX `index_playlist_stream_join_stream_id` "
                    + "ON `playlist_stream_join` (`stream_id`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `remote_playlists` "
                    + "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, "
                    + "`thumbnail_url` TEXT, `uploader` TEXT, `stream_count` INTEGER)");
            database.execSQL("CREATE  INDEX `index_remote_playlists_name` "
                    + "ON `remote_playlists` (`name`)");
            database.execSQL("CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` "
                    + "ON `remote_playlists` (`service_id`, `url`)");

            // Populate streams table with existing entries in watch history
            // Latest data first, thus ignoring older entries with the same indices
            database.execSQL("INSERT OR IGNORE INTO streams (service_id, url, title, "
                    + "stream_type, duration, uploader, thumbnail_url) "

                    + "SELECT service_id, url, title, 'VIDEO_STREAM', duration, "
                    + "uploader, thumbnail_url "

                    + "FROM watch_history "
                    + "ORDER BY creation_date DESC");

            // Once the streams have PKs, join them with the normalized history table
            // and populate it with the remaining data from watch history
            database.execSQL("INSERT INTO stream_history (stream_id, access_date, repeat_count)"
                    + "SELECT uid, creation_date, 1 "
                    + "FROM watch_history INNER JOIN streams "
                    + "ON watch_history.service_id == streams.service_id "
                    + "AND watch_history.url == streams.url "
                    + "ORDER BY creation_date DESC");

            database.execSQL("DROP TABLE IF EXISTS watch_history");

            if (DEBUG) {
                Log.d(TAG, "Stop migrating database");
            }
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(DB_VER_2, DB_VER_3) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            // Add NOT NULLs and new fields
            database.execSQL("CREATE TABLE IF NOT EXISTS streams_new "
                    + "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "service_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT NOT NULL, "
                    + "stream_type TEXT NOT NULL, duration INTEGER NOT NULL, "
                    + "uploader TEXT NOT NULL, thumbnail_url TEXT, view_count INTEGER, "
                    + "textual_upload_date TEXT, upload_date INTEGER, "
                    + "is_upload_date_approximation INTEGER)");

            database.execSQL("INSERT INTO streams_new (uid, service_id, url, title, stream_type, "
                    + "duration, uploader, thumbnail_url, view_count, textual_upload_date, "
                    + "upload_date, is_upload_date_approximation) "

                    + "SELECT uid, service_id, url, ifnull(title, ''), "
                    + "ifnull(stream_type, 'VIDEO_STREAM'), ifnull(duration, 0), "
                    + "ifnull(uploader, ''), ifnull(thumbnail_url, ''), NULL, NULL, NULL, NULL "

                    + "FROM streams WHERE url IS NOT NULL");

            database.execSQL("DROP TABLE streams");
            database.execSQL("ALTER TABLE streams_new RENAME TO streams");
            database.execSQL("CREATE UNIQUE INDEX index_streams_service_id_url "
                    + "ON streams (service_id, url)");

            // Tables for feed feature
            database.execSQL("CREATE TABLE IF NOT EXISTS feed "
                    + "(stream_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, "
                    + "PRIMARY KEY(stream_id, subscription_id), "
                    + "FOREIGN KEY(stream_id) REFERENCES streams(uid) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, "
                    + "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
            database.execSQL("CREATE INDEX index_feed_subscription_id ON feed (subscription_id)");
            database.execSQL("CREATE TABLE IF NOT EXISTS feed_group "
                    + "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, "
                    + "icon_id INTEGER NOT NULL, sort_order INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX index_feed_group_sort_order ON feed_group (sort_order)");
            database.execSQL("CREATE TABLE IF NOT EXISTS feed_group_subscription_join "
                    + "(group_id INTEGER NOT NULL, subscription_id INTEGER NOT NULL, "
                    + "PRIMARY KEY(group_id, subscription_id), "
                    + "FOREIGN KEY(group_id) REFERENCES feed_group(uid) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, "
                    + "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
            database.execSQL("CREATE INDEX index_feed_group_subscription_join_subscription_id "
                    + "ON feed_group_subscription_join (subscription_id)");
            database.execSQL("CREATE TABLE IF NOT EXISTS feed_last_updated "
                    + "(subscription_id INTEGER NOT NULL, last_updated INTEGER, "
                    + "PRIMARY KEY(subscription_id), "
                    + "FOREIGN KEY(subscription_id) REFERENCES subscriptions(uid) "
                    + "ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(DB_VER_3, DB_VER_4) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE streams ADD COLUMN uploader_url TEXT"
            );
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(DB_VER_4, DB_VER_5) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `notification_mode` "
                    + "INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(DB_VER_5, DB_VER_6) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `playlists` ADD COLUMN `is_thumbnail_permanent` "
                    + "INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(DB_VER_6, DB_VER_7) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            // Create a new column thumbnail_stream_id
            database.execSQL("ALTER TABLE `playlists` ADD COLUMN `thumbnail_stream_id` "
                    + "INTEGER NOT NULL DEFAULT -1");

            // Migrate the thumbnail_url to the thumbnail_stream_id
            database.execSQL("UPDATE playlists SET thumbnail_stream_id = ("
                    + " SELECT CASE WHEN COUNT(*) != 0 then stream_uid ELSE -1 END"
                    + " FROM ("
                    + " SELECT p.uid AS playlist_uid, s.uid AS stream_uid"
                    + " FROM playlists p"
                    + " LEFT JOIN playlist_stream_join ps ON p.uid = ps.playlist_id"
                    + " LEFT JOIN streams s ON s.uid = ps.stream_id"
                    + " WHERE s.thumbnail_url = p.thumbnail_url) AS temporary_table"
                    + " WHERE playlist_uid = playlists.uid)");

            // Remove the thumbnail_url field in the playlist table
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlists_new`"
                    + "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "name TEXT, "
                    + "is_thumbnail_permanent INTEGER NOT NULL, "
                    + "thumbnail_stream_id INTEGER NOT NULL)");

            database.execSQL("INSERT INTO playlists_new"
                    + " SELECT uid, name, is_thumbnail_permanent, thumbnail_stream_id "
                    + " FROM playlists");


            database.execSQL("DROP TABLE playlists");
            database.execSQL("ALTER TABLE playlists_new RENAME TO playlists");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_playlists_name` ON `playlists` (`name`)");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(DB_VER_7, DB_VER_8) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM (SELECT "
                    + "MIN(id) as id FROM search_history GROUP BY trim(search), service_id ) tmp)");
            database.execSQL("UPDATE search_history SET search = trim(search)");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(DB_VER_8, DB_VER_9) {
        @Override
        public void migrate(@NonNull final SupportSQLiteDatabase database) {
            try {
                database.beginTransaction();

                // Update playlists.
                // Create a temp table to initialize display_index.
                database.execSQL("CREATE TABLE `playlists_tmp` "
                        + "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                        + "`name` TEXT, `is_thumbnail_permanent` INTEGER NOT NULL, "
                        + "`thumbnail_stream_id` INTEGER NOT NULL, "
                        + "`display_index` INTEGER NOT NULL)");
                database.execSQL("INSERT INTO `playlists_tmp` "
                        + "(`uid`, `name`, `is_thumbnail_permanent`, `thumbnail_stream_id`, "
                        + "`display_index`) "
                        + "SELECT `uid`, `name`, `is_thumbnail_permanent`, `thumbnail_stream_id`, "
                        + "-1 "
                        + "FROM `playlists`");

                // Replace the old table, note that this also removes the index on the name which
                // we don't need anymore.
                database.execSQL("DROP TABLE `playlists`");
                database.execSQL("ALTER TABLE `playlists_tmp` RENAME TO `playlists`");


                // Update remote_playlists.
                // Create a temp table to initialize display_index.
                database.execSQL("CREATE TABLE `remote_playlists_tmp` "
                        + "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                        + "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, "
                        + "`thumbnail_url` TEXT, `uploader` TEXT, "
                        + "`display_index` INTEGER NOT NULL,"
                        + "`stream_count` INTEGER)");
                database.execSQL("INSERT INTO `remote_playlists_tmp` (`uid`, `service_id`, "
                        + "`name`, `url`, `thumbnail_url`, `uploader`, `display_index`, "
                        + "`stream_count`)"
                        + "SELECT `uid`, `service_id`, `name`, `url`, `thumbnail_url`, `uploader`, "
                        + "-1, `stream_count` FROM `remote_playlists`");

                // Replace the old table, note that this also removes the index on the name which
                // we don't need anymore.
                database.execSQL("DROP TABLE `remote_playlists`");
                database.execSQL("ALTER TABLE `remote_playlists_tmp` RENAME TO `remote_playlists`");

                // Create index on the new table.
                database.execSQL("CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` "
                        + "ON `remote_playlists` (`service_id`, `url`)");

                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    };

    private Migrations() {
    }
}
