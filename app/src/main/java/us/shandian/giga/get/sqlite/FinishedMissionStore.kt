package us.shandian.giga.get.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import java.io.File
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission

/**
 * SQLite helper to store finished [us.shandian.giga.get.FinishedMission]'s
 */
class FinishedMissionStore(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MISSIONS_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        if (version == 2) {
            db.execSQL("ALTER TABLE $MISSIONS_TABLE_NAME_v2 ADD COLUMN $KEY_KIND TEXT;")
            version++
        }

        if (version == 3) {
            val keyLocation = "location"
            val keyName = "name"

            db.execSQL(MISSIONS_CREATE_TABLE)

            db.query(
                MISSIONS_TABLE_NAME_v2,
                null,
                null,
                null,
                null,
                null,
                KEY_TIMESTAMP
            ).use { cursor ->
                if (cursor.count > 0) {
                    db.beginTransaction()
                    try {
                        while (cursor.moveToNext()) {
                            val values = ContentValues().apply {
                                put(KEY_SOURCE, cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE)))
                                put(KEY_DONE, cursor.getString(cursor.getColumnIndexOrThrow(KEY_DONE)))
                                put(KEY_TIMESTAMP, cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP)))
                                put(KEY_KIND, cursor.getString(cursor.getColumnIndexOrThrow(KEY_KIND)))
                                put(
                                    KEY_PATH,
                                    Uri.fromFile(
                                        File(
                                            cursor.getString(cursor.getColumnIndexOrThrow(keyLocation)),
                                            cursor.getString(cursor.getColumnIndexOrThrow(keyName))
                                        )
                                    ).toString()
                                )
                            }
                            db.insert(FINISHED_TABLE_NAME, null, values)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            db.execSQL("DROP TABLE $MISSIONS_TABLE_NAME_v2")
        }
    }

    /**
     * Returns all values of the download mission as ContentValues.
     *
     * @param downloadMission the download mission
     * @return the content values
     */
    private fun getValuesOfMission(downloadMission: Mission): ContentValues {
        return ContentValues().apply {
            put(KEY_SOURCE, downloadMission.source)
            put(KEY_PATH, downloadMission.storage?.getUri()?.toString() ?: "")
            put(KEY_DONE, downloadMission.length)
            put(KEY_TIMESTAMP, downloadMission.timestamp)
            put(KEY_KIND, downloadMission.kind.toString())
        }
    }

    private fun getMissionFromCursor(cursor: Cursor): FinishedMission {
        var kind = cursor.getString(cursor.getColumnIndexOrThrow(KEY_KIND))
        if (kind.isNullOrEmpty()) kind = "?"

        val path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH))

        return FinishedMission().apply {
            source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE))
            length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE))
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP))
            this.kind = kind[0]

            storage = try {
                StoredFileHelper(context, null, Uri.parse(path), "")
            } catch (e: Exception) {
                Log.e("FinishedMissionStore", "failed to load the storage path of: $path", e)
                StoredFileHelper(null, path, "", "")
            }
        }
    }

    // ////////////////////////////////
    // Data source methods
    // /////////////////////////////////

    fun loadFinishedMissions(): ArrayList<FinishedMission> {
        val database = readableDatabase
        database.query(
            FINISHED_TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$KEY_TIMESTAMP DESC"
        ).use { cursor ->
            val count = cursor.count
            if (count == 0) return ArrayList(1)

            val result = ArrayList<FinishedMission>(count)
            while (cursor.moveToNext()) {
                result.add(getMissionFromCursor(cursor))
            }
            return result
        }
    }

    fun addFinishedMission(downloadMission: DownloadMission) {
        val values = getValuesOfMission(downloadMission)
        writableDatabase.insert(FINISHED_TABLE_NAME, null, values)
    }

    fun deleteMission(mission: Mission) {
        val ts = mission.timestamp.toString()
        val database = writableDatabase

        if (mission is FinishedMission) {
            if (mission.storage?.isInvalid() != false) {
                database.delete(FINISHED_TABLE_NAME, "$KEY_TIMESTAMP = ?", arrayOf(ts))
            } else {
                database.delete(
                    FINISHED_TABLE_NAME,
                    "$KEY_TIMESTAMP = ? AND $KEY_PATH = ?",
                    arrayOf(ts, mission.storage?.getUri()?.toString() ?: "")
                )
            }
        } else {
            throw UnsupportedOperationException("DownloadMission")
        }
    }

    fun updateMission(mission: Mission) {
        val values = getValuesOfMission(mission)
        val database = writableDatabase
        val ts = mission.timestamp.toString()

        val rowsAffected = if (mission is FinishedMission) {
            if (mission.storage?.isInvalid() != false) {
                database.update(FINISHED_TABLE_NAME, values, "$KEY_TIMESTAMP = ?", arrayOf(ts))
            } else {
                database.update(
                    FINISHED_TABLE_NAME,
                    values,
                    "$KEY_PATH = ?",
                    arrayOf(mission.storage?.getUri()?.toString() ?: "")
                )
            }
        } else {
            throw UnsupportedOperationException("DownloadMission")
        }

        if (rowsAffected != 1) {
            Log.e("FinishedMissionStore", "Expected 1 row to be affected by update but got $rowsAffected")
        }
    }

    companion object {
        private const val DATABASE_NAME = "downloads.db"
        private const val DATABASE_VERSION = 4

        /**
         * The table name of download missions (old)
         */
        private const val MISSIONS_TABLE_NAME_v2 = "download_missions"

        /**
         * The table name of download missions
         */
        private const val FINISHED_TABLE_NAME = "finished_missions"

        /**
         * The key to the urls of a mission
         */
        private const val KEY_SOURCE = "url"

        /**
         * The key to the done.
         */
        private const val KEY_DONE = "bytes_downloaded"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_KIND = "kind"
        private const val KEY_PATH = "path"

        /**
         * The statement to create the table
         */
        private const val MISSIONS_CREATE_TABLE =
            (
                "CREATE TABLE " + FINISHED_TABLE_NAME + " (" +
                    KEY_PATH + " TEXT NOT NULL, " +
                    KEY_SOURCE + " TEXT NOT NULL, " +
                    KEY_DONE + " INTEGER NOT NULL, " +
                    KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                    KEY_KIND + " TEXT NOT NULL, " +
                    " UNIQUE(" + KEY_TIMESTAMP + ", " + KEY_PATH + "));"
                )
    }
}
