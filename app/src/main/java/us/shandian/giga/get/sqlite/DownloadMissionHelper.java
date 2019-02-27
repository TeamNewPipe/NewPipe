package us.shandian.giga.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;

/**
 * SQLiteHelper to store finished {@link us.shandian.giga.get.DownloadMission}'s
 */
public class DownloadMissionHelper extends SQLiteOpenHelper {
    private final String TAG = "DownloadMissionHelper";

    // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
    private static final String DATABASE_NAME = "downloads.db";

    private static final int DATABASE_VERSION = 3;

    /**
     * The table name of download missions
     */
    static final String MISSIONS_TABLE_NAME = "download_missions";

    /**
     * The key to the directory location of the mission
     */
    static final String KEY_LOCATION = "location";
    /**
     * The key to the urls of a mission
     */
    static final String KEY_SOURCE_URL = "url";
    /**
     * The key to the name of a mission
     */
    static final String KEY_NAME = "name";

    /**
     * The key to the done.
     */
    static final String KEY_DONE = "bytes_downloaded";

    static final String KEY_TIMESTAMP = "timestamp";

    static  final String KEY_KIND = "kind";

    /**
     * The statement to create the table
     */
    private static final String MISSIONS_CREATE_TABLE =
            "CREATE TABLE " + MISSIONS_TABLE_NAME + " (" +
                    KEY_LOCATION + " TEXT NOT NULL, " +
                    KEY_NAME + " TEXT NOT NULL, " +
                    KEY_SOURCE_URL + " TEXT NOT NULL, " +
                    KEY_DONE + " INTEGER NOT NULL, " +
                    KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                    KEY_KIND + " TEXT NOT NULL, " +
                    " UNIQUE(" + KEY_LOCATION + ", " + KEY_NAME + "));";

    public DownloadMissionHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MISSIONS_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 2) {
            db.execSQL("ALTER TABLE " + MISSIONS_TABLE_NAME + " ADD COLUMN " + KEY_KIND + " TEXT;");
        }
    }

    /**
     * Returns all values of the download mission as ContentValues.
     *
     * @param downloadMission the download mission
     * @return the content values
     */
    public static ContentValues getValuesOfMission(DownloadMission downloadMission) {
        ContentValues values = new ContentValues();
        values.put(KEY_SOURCE_URL, downloadMission.source);
        values.put(KEY_LOCATION, downloadMission.location);
        values.put(KEY_NAME, downloadMission.name);
        values.put(KEY_DONE, downloadMission.done);
        values.put(KEY_TIMESTAMP, downloadMission.timestamp);
        values.put(KEY_KIND, String.valueOf(downloadMission.kind));
        return values;
    }

    public static FinishedMission getMissionFromCursor(Cursor cursor) {
        if (cursor == null) throw new NullPointerException("cursor is null");

        String kind = cursor.getString(cursor.getColumnIndex(KEY_KIND));
        if (kind == null || kind.isEmpty()) kind = "?";

        FinishedMission mission = new FinishedMission();
        mission.name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME));
        mission.location = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION));
        mission.source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE_URL));;
        mission.length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE));
        mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
        mission.kind = kind.charAt(0);

        return mission;
    }
}
