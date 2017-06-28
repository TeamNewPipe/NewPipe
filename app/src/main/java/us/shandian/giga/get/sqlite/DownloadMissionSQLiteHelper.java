package us.shandian.giga.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import us.shandian.giga.get.DownloadMission;

/**
 * SqliteHelper to store {@link us.shandian.giga.get.DownloadMission}
 */
public class DownloadMissionSQLiteHelper extends SQLiteOpenHelper {


    private final String TAG = "DownloadMissionHelper";

    // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
    private static final String DATABASE_NAME = "downloads.db";

    private static final int DATABASE_VERSION = 2;
    /**
     * The table name of download missions
     */
    static final String MISSIONS_TABLE_NAME = "download_missions";

    /**
     * The key to the directory location of the mission
     */
    static final String KEY_LOCATION = "location";
    /**
     * The key to the url of a mission
     */
    static final String KEY_URL = "url";
    /**
     * The key to the name of a mission
     */
    static final String KEY_NAME = "name";

    /**
     * The key to the done.
     */
    static final String KEY_DONE = "bytes_downloaded";

    static final String KEY_TIMESTAMP = "timestamp";

    /**
     * The statement to create the table
     */
    private static final String MISSIONS_CREATE_TABLE =
            "CREATE TABLE " + MISSIONS_TABLE_NAME + " (" +
                    KEY_LOCATION + " TEXT NOT NULL, " +
                    KEY_NAME + " TEXT NOT NULL, " +
                    KEY_URL + " TEXT NOT NULL, " +
                    KEY_DONE + " INTEGER NOT NULL, " +
                    KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                    " UNIQUE(" + KEY_LOCATION + ", " + KEY_NAME + "));";


    DownloadMissionSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Returns all values of the download mission as ContentValues.
     *
     * @param downloadMission the download mission
     * @return the content values
     */
    public static ContentValues getValuesOfMission(DownloadMission downloadMission) {
        ContentValues values = new ContentValues();
        values.put(KEY_URL, downloadMission.url);
        values.put(KEY_LOCATION, downloadMission.location);
        values.put(KEY_NAME, downloadMission.name);
        values.put(KEY_DONE, downloadMission.done);
        values.put(KEY_TIMESTAMP, downloadMission.timestamp);
        return values;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MISSIONS_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Currently nothing to do
    }

    public static DownloadMission getMissionFromCursor(Cursor cursor) {
        if (cursor == null) throw new NullPointerException("cursor is null");
        int pos;
        String name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME));
        String location = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION));
        String url = cursor.getString(cursor.getColumnIndexOrThrow(KEY_URL));
        DownloadMission mission = new DownloadMission(name, url, location);
        mission.done = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE));
        mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
        mission.finished = true;
        return mission;
    }
}
