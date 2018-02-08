package org.schabi.newpipe.database;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.migration.Migration;
import android.support.annotation.NonNull;

public class Migrations {

    public static final int DB_VER_11_0 = 1;
    public static final int DB_VER_12_0 = 2;

    public static final Migration MIGRATION_11_12 = new Migration(DB_VER_11_0, DB_VER_12_0) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /*
            * Unfortunately these queries must be hardcoded due to the possibility of
            * schema and names changing at a later date, thus invalidating the older migration
            * scripts if they are not hardcoded.
            * */

            // Not much we can do about this, since room doesn't create tables before migration.
            // It's either this or blasting the entire database anew.
            database.execSQL("CREATE  INDEX `index_search_history_search` ON `search_history` (`search`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `streams` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `service_id` INTEGER NOT NULL, `url` TEXT, `title` TEXT, `stream_type` TEXT, `duration` INTEGER, `uploader` TEXT, `thumbnail_url` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX `index_streams_service_id_url` ON `streams` (`service_id`, `url`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `stream_history` (`stream_id` INTEGER NOT NULL, `access_date` INTEGER NOT NULL, `repeat_count` INTEGER NOT NULL, PRIMARY KEY(`stream_id`, `access_date`), FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("CREATE  INDEX `index_stream_history_stream_id` ON `stream_history` (`stream_id`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `stream_state` (`stream_id` INTEGER NOT NULL, `progress_time` INTEGER NOT NULL, PRIMARY KEY(`stream_id`), FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `thumbnail_url` TEXT)");
            database.execSQL("CREATE  INDEX `index_playlists_name` ON `playlists` (`name`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_stream_join` (`playlist_id` INTEGER NOT NULL, `stream_id` INTEGER NOT NULL, `join_index` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `join_index`), FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`stream_id`) REFERENCES `streams`(`uid`) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)");
            database.execSQL("CREATE UNIQUE INDEX `index_playlist_stream_join_playlist_id_join_index` ON `playlist_stream_join` (`playlist_id`, `join_index`)");
            database.execSQL("CREATE  INDEX `index_playlist_stream_join_stream_id` ON `playlist_stream_join` (`stream_id`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `remote_playlists` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, `thumbnail_url` TEXT, `uploader` TEXT, `stream_count` INTEGER)");
            database.execSQL("CREATE  INDEX `index_remote_playlists_name` ON `remote_playlists` (`name`)");
            database.execSQL("CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` ON `remote_playlists` (`service_id`, `url`)");

            // Populate streams table with existing entries in watch history
            // Latest data first, thus ignoring older entries with the same indices
            database.execSQL("INSERT OR IGNORE INTO streams (service_id, url, title, " +
                    "stream_type, duration, uploader, thumbnail_url) " +

                    "SELECT service_id, url, title, 'VIDEO_STREAM', duration, " +
                    "uploader, thumbnail_url " +

                    "FROM watch_history " +
                    "ORDER BY creation_date DESC");

            // Once the streams have PKs, join them with the normalized history table
            // and populate it with the remaining data from watch history
            database.execSQL("INSERT INTO stream_history (stream_id, access_date, repeat_count)" +
                    "SELECT uid, creation_date, 1 " +
                    "FROM watch_history INNER JOIN streams " +
                    "ON watch_history.service_id == streams.service_id " +
                    "AND watch_history.url == streams.url " +
                    "ORDER BY creation_date DESC");

            database.execSQL("DROP TABLE IF EXISTS watch_history");
        }
    };
}
