package us.shandian.giga.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.io.StoredFileHelper;

/**
 * SQLite helper to store finished {@link us.shandian.giga.get.FinishedMission}'s
 */
public class FinishedMissionStore extends SQLiteOpenHelper {

    // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
    private static final String DATABASE_NAME = "downloads.db";

    private static final int DATABASE_VERSION = 4;

    /**
     * The table name of download missions (old)
     */
    private static final String MISSIONS_TABLE_NAME_v2 = "download_missions";

    /**
     * The table name of download missions
     */
    private static final String FINISHED_TABLE_NAME = "finished_missions";

    /**
     * The key to the urls of a mission
     */
    private static final String KEY_SOURCE = "url";


    /**
     * The key to the done.
     */
    private static final String KEY_DONE = "bytes_downloaded";

    private static final String KEY_TIMESTAMP = "timestamp";

    private static final String KEY_KIND = "kind";

    private static final String KEY_PATH = "path";

    /**
     * The statement to create the table
     */
    private static final String MISSIONS_CREATE_TABLE =
            "CREATE TABLE " + FINISHED_TABLE_NAME + " (" +
                    KEY_PATH + " TEXT NOT NULL, " +
                    KEY_SOURCE + " TEXT NOT NULL, " +
                    KEY_DONE + " INTEGER NOT NULL, " +
                    KEY_TIMESTAMP + " INTEGER NOT NULL, " +
                    KEY_KIND + " TEXT NOT NULL, " +
                    " UNIQUE(" + KEY_TIMESTAMP + ", " + KEY_PATH + "));";


    private Context context;

    public FinishedMissionStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MISSIONS_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 2) {
            db.execSQL("ALTER TABLE " + MISSIONS_TABLE_NAME_v2 + " ADD COLUMN " + KEY_KIND + " TEXT;");
            oldVersion++;
        }

        if (oldVersion == 3) {
            final String KEY_LOCATION = "location";
            final String KEY_NAME = "name";

            db.execSQL(MISSIONS_CREATE_TABLE);

            Cursor cursor = db.query(MISSIONS_TABLE_NAME_v2, null, null,
                    null, null, null, KEY_TIMESTAMP);

            int count = cursor.getCount();
            if (count > 0) {
                db.beginTransaction();
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put(KEY_SOURCE, cursor.getString(cursor.getColumnIndex(KEY_SOURCE)));
                    values.put(KEY_DONE, cursor.getString(cursor.getColumnIndex(KEY_DONE)));
                    values.put(KEY_TIMESTAMP, cursor.getLong(cursor.getColumnIndex(KEY_TIMESTAMP)));
                    values.put(KEY_KIND, cursor.getString(cursor.getColumnIndex(KEY_KIND)));
                    values.put(KEY_PATH, Uri.fromFile(
                            new File(
                                    cursor.getString(cursor.getColumnIndex(KEY_LOCATION)),
                                    cursor.getString(cursor.getColumnIndex(KEY_NAME))
                            )
                    ).toString());

                    db.insert(FINISHED_TABLE_NAME, null, values);
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            }

            cursor.close();
            db.execSQL("DROP TABLE " + MISSIONS_TABLE_NAME_v2);
        }
    }

    /**
     * Returns all values of the download mission as ContentValues.
     *
     * @param downloadMission the download mission
     * @return the content values
     */
    private ContentValues getValuesOfMission(@NonNull Mission downloadMission) {
        ContentValues values = new ContentValues();
        values.put(KEY_SOURCE, downloadMission.source);
        values.put(KEY_PATH, downloadMission.storage.getUri().toString());
        values.put(KEY_DONE, downloadMission.length);
        values.put(KEY_TIMESTAMP, downloadMission.timestamp);
        values.put(KEY_KIND, String.valueOf(downloadMission.kind));
        return values;
    }

    private FinishedMission getMissionFromCursor(Cursor cursor) {
        if (cursor == null) throw new NullPointerException("cursor is null");

        String kind = cursor.getString(cursor.getColumnIndex(KEY_KIND));
        if (kind == null || kind.isEmpty()) kind = "?";

        String path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH));

        FinishedMission mission = new FinishedMission();

        mission.source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE));
        mission.length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE));
        mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
        mission.kind = kind.charAt(0);

        try {
            mission.storage = new StoredFileHelper(context,null, Uri.parse(path), "");
        } catch (Exception e) {
            Log.e("FinishedMissionStore", "failed to load the storage path of: " + path, e);
            mission.storage = new StoredFileHelper(null, path, "", "");
        }

        return mission;
    }


    //////////////////////////////////
    // Data source methods
    ///////////////////////////////////

    public ArrayList<FinishedMission> loadFinishedMissions() {
        SQLiteDatabase database = getReadableDatabase();
        Cursor cursor = database.query(FINISHED_TABLE_NAME, null, null,
                null, null, null, KEY_TIMESTAMP + " DESC");

        int count = cursor.getCount();
        if (count == 0) return new ArrayList<>(1);

        ArrayList<FinishedMission> result = new ArrayList<>(count);
        while (cursor.moveToNext()) {
            result.add(getMissionFromCursor(cursor));
        }

        return result;
    }

    public void addFinishedMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = getValuesOfMission(downloadMission);
        database.insert(FINISHED_TABLE_NAME, null, values);
    }

    public void deleteMission(Mission mission) {
        if (mission == null) throw new NullPointerException("mission is null");
        String ts = String.valueOf(mission.timestamp);

        SQLiteDatabase database = getWritableDatabase();

        if (mission instanceof FinishedMission) {
            if (mission.storage.isInvalid()) {
                database.delete(FINISHED_TABLE_NAME, KEY_TIMESTAMP + " = ?", new String[]{ts});
            } else {
                database.delete(FINISHED_TABLE_NAME, KEY_TIMESTAMP + " = ? AND " + KEY_PATH + " = ?", new String[]{
                        ts, mission.storage.getUri().toString()
                });
            }
        } else {
            throw new UnsupportedOperationException("DownloadMission");
        }
    }

    public void updateMission(Mission mission) {
        if (mission == null) throw new NullPointerException("mission is null");
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = getValuesOfMission(mission);
        String ts = String.valueOf(mission.timestamp);

        int rowsAffected;

        if (mission instanceof FinishedMission) {
            if (mission.storage.isInvalid()) {
                rowsAffected = database.update(FINISHED_TABLE_NAME, values, KEY_TIMESTAMP + " = ?", new String[]{ts});
            } else {
                rowsAffected = database.update(FINISHED_TABLE_NAME, values, KEY_PATH + " = ?", new String[]{
                        mission.storage.getUri().toString()
                });
            }
        } else {
            throw new UnsupportedOperationException("DownloadMission");
        }

        if (rowsAffected != 1) {
            Log.e("FinishedMissionStore", "Expected 1 row to be affected by update but got " + rowsAffected);
        }
    }
}
