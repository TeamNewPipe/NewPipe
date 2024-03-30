package us.shandian.giga.get.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission
import java.io.File
import java.util.Objects

/**
 * SQLite helper to store finished [us.shandian.giga.get.FinishedMission]'s
 */
class FinishedMissionStore(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    public override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(MISSIONS_CREATE_TABLE)
    }

    public override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var oldVersion: Int = oldVersion
        if (oldVersion == 2) {
            db.execSQL("ALTER TABLE " + MISSIONS_TABLE_NAME_v2 + " ADD COLUMN " + KEY_KIND + " TEXT;")
            oldVersion++
        }
        if (oldVersion == 3) {
            val KEY_LOCATION: String = "location"
            val KEY_NAME: String = "name"
            db.execSQL(MISSIONS_CREATE_TABLE)
            val cursor: Cursor = db.query(MISSIONS_TABLE_NAME_v2, null, null,
                    null, null, null, KEY_TIMESTAMP)
            val count: Int = cursor.getCount()
            if (count > 0) {
                db.beginTransaction()
                while (cursor.moveToNext()) {
                    val values: ContentValues = ContentValues()
                    values.put(KEY_SOURCE, cursor.getString(cursor.getColumnIndex(KEY_SOURCE)))
                    values.put(KEY_DONE, cursor.getString(cursor.getColumnIndex(KEY_DONE)))
                    values.put(KEY_TIMESTAMP, cursor.getLong(cursor.getColumnIndex(KEY_TIMESTAMP)))
                    values.put(KEY_KIND, cursor.getString(cursor.getColumnIndex(KEY_KIND)))
                    values.put(KEY_PATH, Uri.fromFile(
                            File(
                                    cursor.getString(cursor.getColumnIndex(KEY_LOCATION)),
                                    cursor.getString(cursor.getColumnIndex(KEY_NAME))
                            )
                    ).toString())
                    db.insert(FINISHED_TABLE_NAME, null, values)
                }
                db.setTransactionSuccessful()
                db.endTransaction()
            }
            cursor.close()
            db.execSQL("DROP TABLE " + MISSIONS_TABLE_NAME_v2)
        }
    }

    /**
     * Returns all values of the download mission as ContentValues.
     *
     * @param downloadMission the download mission
     * @return the content values
     */
    private fun getValuesOfMission(downloadMission: Mission): ContentValues {
        val values: ContentValues = ContentValues()
        values.put(KEY_SOURCE, downloadMission.source)
        values.put(KEY_PATH, downloadMission.storage!!.getUri().toString())
        values.put(KEY_DONE, downloadMission.length)
        values.put(KEY_TIMESTAMP, downloadMission.timestamp)
        values.put(KEY_KIND, downloadMission.kind.toString())
        return values
    }

    private fun getMissionFromCursor(cursor: Cursor): FinishedMission {
        var kind: String? = Objects.requireNonNull(cursor).getString(cursor.getColumnIndex(KEY_KIND))
        if (kind == null || kind.isEmpty()) kind = "?"
        val path: String = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH))
        val mission: FinishedMission = FinishedMission()
        mission.source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE))
        mission.length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE))
        mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP))
        mission.kind = kind.get(0)
        try {
            mission.storage = StoredFileHelper(context, null, Uri.parse(path), "")
        } catch (e: Exception) {
            Log.e("FinishedMissionStore", "failed to load the storage path of: " + path, e)
            mission.storage = StoredFileHelper(null, path, "", "")
        }
        return mission
    }

    //////////////////////////////////
    // Data source methods
    ///////////////////////////////////
    fun loadFinishedMissions(): ArrayList<FinishedMission> {
        val database: SQLiteDatabase = getReadableDatabase()
        val cursor: Cursor = database.query(FINISHED_TABLE_NAME, null, null,
                null, null, null, KEY_TIMESTAMP + " DESC")
        val count: Int = cursor.getCount()
        if (count == 0) return ArrayList(1)
        val result: ArrayList<FinishedMission> = ArrayList(count)
        while (cursor.moveToNext()) {
            result.add(getMissionFromCursor(cursor))
        }
        return result
    }

    fun addFinishedMission(downloadMission: DownloadMission) {
        val values: ContentValues = getValuesOfMission(Objects.requireNonNull(downloadMission))
        val database: SQLiteDatabase = getWritableDatabase()
        database.insert(FINISHED_TABLE_NAME, null, values)
    }

    fun deleteMission(mission: Mission) {
        val ts: String = Objects.requireNonNull(mission).timestamp.toString()
        val database: SQLiteDatabase = getWritableDatabase()
        if (mission is FinishedMission) {
            if (mission.storage!!.isInvalid()) {
                database.delete(FINISHED_TABLE_NAME, KEY_TIMESTAMP + " = ?", arrayOf(ts))
            } else {
                database.delete(FINISHED_TABLE_NAME, KEY_TIMESTAMP + " = ? AND " + KEY_PATH + " = ?", arrayOf(
                        ts, mission.storage!!.getUri().toString()
                ))
            }
        } else {
            throw UnsupportedOperationException("DownloadMission")
        }
    }

    fun updateMission(mission: Mission) {
        val values: ContentValues = getValuesOfMission(Objects.requireNonNull(mission))
        val database: SQLiteDatabase = getWritableDatabase()
        val ts: String = mission.timestamp.toString()
        val rowsAffected: Int
        if (mission is FinishedMission) {
            if (mission.storage!!.isInvalid()) {
                rowsAffected = database.update(FINISHED_TABLE_NAME, values, KEY_TIMESTAMP + " = ?", arrayOf(ts))
            } else {
                rowsAffected = database.update(FINISHED_TABLE_NAME, values, KEY_PATH + " = ?", arrayOf(
                        mission.storage!!.getUri().toString()
                ))
            }
        } else {
            throw UnsupportedOperationException("DownloadMission")
        }
        if (rowsAffected != 1) {
            Log.e("FinishedMissionStore", "Expected 1 row to be affected by update but got " + rowsAffected)
        }
    }

    companion object {
        // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
        private val DATABASE_NAME: String = "downloads.db"
        private val DATABASE_VERSION: Int = 4

        /**
         * The table name of download missions (old)
         */
        private val MISSIONS_TABLE_NAME_v2: String = "download_missions"

        /**
         * The table name of download missions
         */
        private val FINISHED_TABLE_NAME: String = "finished_missions"

        /**
         * The key to the urls of a mission
         */
        private val KEY_SOURCE: String = "url"

        /**
         * The key to the done.
         */
        private val KEY_DONE: String = "bytes_downloaded"
        private val KEY_TIMESTAMP: String = "timestamp"
        private val KEY_KIND: String = "kind"
        private val KEY_PATH: String = "path"

        /**
         * The statement to create the table
         */
        private val MISSIONS_CREATE_TABLE: String = ("CREATE TABLE " + FINISHED_TABLE_NAME + " (" +
                KEY_PATH + " TEXT NOT NULL, " +
                KEY_SOURCE + " TEXT NOT NULL, " +
                KEY_DONE + " INTEGER NOT NULL, " +
                KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                KEY_KIND + " TEXT NOT NULL, " +
                " UNIQUE(" + KEY_TIMESTAMP + ", " + KEY_PATH + "));")
    }
}
