package us.shandian.giga.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import org.schabi.newpipe.streams.io.StoredFileHelper;

/**
 * SQLite helper to store finished {@link us.shandian.giga.get.FinishedMission}'s
 */
public class FinishedMissionStore extends SQLiteOpenHelper {

    // TODO: use NewPipeSQLiteHelper ('s constants) when playlist branch is merged (?)
    private static final String DATABASE_NAME = "downloads.db";

    private static final int DATABASE_VERSION = 6;

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

    private static final String KEY_SYNC_ID = "sync_id";

    private static final String KEY_DISPLAY_NAME = "display_name";

    private static final String KEY_MIME_TYPE = "mime_type";

    private static final String KEY_TITLE = "title";

    private static final String KEY_UPLOADER = "uploader";

    private static final String KEY_DESCRIPTION = "description";

    private static final String KEY_UPLOAD_DATE = "upload_date";

    private static final String KEY_DURATION = "duration";

    private static final String KEY_TAGS = "tags";

    private static final String KEY_THUMBNAIL_URL = "thumbnail_url";

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
                    KEY_SYNC_ID + " TEXT NOT NULL DEFAULT '', " +
                    KEY_DISPLAY_NAME + " TEXT NOT NULL DEFAULT '', " +
                    KEY_MIME_TYPE + " TEXT NOT NULL DEFAULT '" +
                    StoredFileHelper.DEFAULT_MIME + "', " +
                    KEY_TITLE + " TEXT NOT NULL DEFAULT '', " +
                    KEY_UPLOADER + " TEXT NOT NULL DEFAULT '', " +
                    KEY_DESCRIPTION + " TEXT NOT NULL DEFAULT '', " +
                    KEY_UPLOAD_DATE + " TEXT NOT NULL DEFAULT '', " +
                    KEY_DURATION + " INTEGER NOT NULL DEFAULT -1, " +
                    KEY_TAGS + " TEXT NOT NULL DEFAULT '', " +
                    KEY_THUMBNAIL_URL + " TEXT NOT NULL DEFAULT '', " +
                    " UNIQUE(" + KEY_TIMESTAMP + ", " + KEY_PATH + "));";


    private final Context context;

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
                    values.put(
                            KEY_SOURCE,
                            cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE))
                    );
                    values.put(
                            KEY_DONE,
                            cursor.getString(cursor.getColumnIndexOrThrow(KEY_DONE))
                    );
                    values.put(
                            KEY_TIMESTAMP,
                            cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP))
                    );
                    values.put(KEY_KIND, cursor.getString(cursor.getColumnIndexOrThrow(KEY_KIND)));
                    values.put(KEY_PATH, Uri.fromFile(
                            new File(
                                    cursor.getString(cursor.getColumnIndexOrThrow(KEY_LOCATION)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME))
                            )
                    ).toString());

                    db.insert(FINISHED_TABLE_NAME, null, values);
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            }

            cursor.close();
            db.execSQL("DROP TABLE " + MISSIONS_TABLE_NAME_v2);
            return;
        }

        if (oldVersion == 4) {
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_SYNC_ID + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_DISPLAY_NAME + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_MIME_TYPE + " TEXT NOT NULL DEFAULT '" +
                    StoredFileHelper.DEFAULT_MIME + "';");
            oldVersion++;
        }

        if (oldVersion == 5) {
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_TITLE + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_UPLOADER + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_DESCRIPTION + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_UPLOAD_DATE + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_DURATION + " INTEGER NOT NULL DEFAULT -1;");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_TAGS + " TEXT NOT NULL DEFAULT '';");
            db.execSQL("ALTER TABLE " + FINISHED_TABLE_NAME + " ADD COLUMN " +
                    KEY_THUMBNAIL_URL + " TEXT NOT NULL DEFAULT '';");
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
        if (downloadMission instanceof FinishedMission) {
            FinishedMission mission = (FinishedMission) downloadMission;
            values.put(KEY_SYNC_ID, mission.syncId);
            values.put(KEY_DISPLAY_NAME, mission.displayName);
            values.put(KEY_MIME_TYPE, mission.mimeType);
            values.put(KEY_TITLE, mission.title == null ? "" : mission.title);
            values.put(KEY_UPLOADER, mission.uploader == null ? "" : mission.uploader);
            values.put(KEY_DESCRIPTION, mission.description == null ? "" : mission.description);
            values.put(KEY_UPLOAD_DATE, mission.uploadDate == null ? "" : mission.uploadDate);
            values.put(KEY_DURATION, mission.duration);
            values.put(KEY_TAGS, mission.tags == null ? "" : mission.tags);
            values.put(KEY_THUMBNAIL_URL, mission.thumbnailUrl == null ? "" : mission.thumbnailUrl);
        }
        return values;
    }

    private FinishedMission getMissionFromCursor(Cursor cursor) {
        String kind = Objects.requireNonNull(cursor)
                .getString(cursor.getColumnIndexOrThrow(KEY_KIND));
        if (kind == null || kind.isEmpty()) kind = "?";

        String path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH));

        FinishedMission mission = new FinishedMission();

        mission.source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE));
        mission.length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE));
        mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
        mission.kind = kind.charAt(0);
        mission.syncId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SYNC_ID));
        mission.displayName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DISPLAY_NAME));
        mission.mimeType = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MIME_TYPE));
        mission.title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE));
        mission.uploader = cursor.getString(cursor.getColumnIndexOrThrow(KEY_UPLOADER));
        mission.description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION));
        mission.uploadDate = cursor.getString(cursor.getColumnIndexOrThrow(KEY_UPLOAD_DATE));
        mission.duration = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DURATION));
        mission.tags = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS));
        mission.thumbnailUrl = cursor.getString(
                cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_URL)
        );

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

    public FinishedMission findByStorageUri(final String storageUri) {
        final SQLiteDatabase database = getWritableDatabase();
        ensureDescriptiveMetadata(database);
        final Uri uri = Uri.parse(storageUri);
        String selection = KEY_PATH + " = ?";
        String[] arguments = new String[]{storageUri};
        if (DocumentsContract.isDocumentUri(context, uri)) {
            final String prefix = "content://" + uri.getAuthority() + "/tree/";
            final String suffix = "/document/" + Uri.encode(DocumentsContract.getDocumentId(uri));
            selection += " OR (instr(" + KEY_PATH + ", ?) = 1 AND substr("
                    + KEY_PATH + ", -length(?)) = ?)";
            arguments = new String[]{storageUri, prefix, suffix, suffix};
        }
        try (Cursor cursor = database.query(FINISHED_TABLE_NAME, null, selection,
                arguments, null, null, KEY_TIMESTAMP + " DESC", "1")) {
            return cursor.moveToFirst() ? getMissionFromCursor(cursor) : null;
        }
    }

    public ArrayList<FinishedMission> loadFinishedMissions() {
        SQLiteDatabase database = getWritableDatabase();
        ensureDescriptiveMetadata(database);
        Cursor cursor = database.query(FINISHED_TABLE_NAME, null, null,
                null, null, null, KEY_TIMESTAMP + " DESC");

        int count = cursor.getCount();
        if (count == 0) {
            cursor.close();
            return new ArrayList<>(1);
        }

        ArrayList<FinishedMission> result = new ArrayList<>(count);
        while (cursor.moveToNext()) {
            result.add(getMissionFromCursor(cursor));
        }
        cursor.close();

        return result;
    }

    public ArrayList<FinishedMission> loadCompletedDownloadMetadata() {
        SQLiteDatabase database = getWritableDatabase();
        ensureDescriptiveMetadata(database);
        String[] columns = {
                KEY_SYNC_ID,
                KEY_SOURCE,
                KEY_DONE,
                KEY_TIMESTAMP,
                KEY_KIND,
                KEY_DISPLAY_NAME,
                KEY_MIME_TYPE
        };
        Cursor cursor = database.query(FINISHED_TABLE_NAME, columns, null,
                null, null, null, KEY_TIMESTAMP + " DESC");
        ArrayList<FinishedMission> result = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            FinishedMission mission = new FinishedMission();
            String kind = cursor.getString(cursor.getColumnIndexOrThrow(KEY_KIND));
            mission.syncId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SYNC_ID));
            mission.source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE));
            mission.length = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DONE));
            mission.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
            mission.kind = kind == null || kind.isEmpty() ? '?' : kind.charAt(0);
            mission.displayName = cursor.getString(
                    cursor.getColumnIndexOrThrow(KEY_DISPLAY_NAME)
            );
            mission.mimeType = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MIME_TYPE));
            mission.storage = new StoredFileHelper(
                    null,
                    mission.displayName,
                    mission.mimeType,
                    ""
            );
            result.add(mission);
        }
        cursor.close();
        return result;
    }

    public void addFinishedMission(FinishedMission finishedMission) {
        ContentValues values = getValuesOfMission(Objects.requireNonNull(finishedMission));
        SQLiteDatabase database = getWritableDatabase();
        database.insert(FINISHED_TABLE_NAME, null, values);
    }

    public void deleteMission(Mission mission) {
        String ts = String.valueOf(Objects.requireNonNull(mission).timestamp);

        SQLiteDatabase database = getWritableDatabase();

        if (mission instanceof FinishedMission) {
            FinishedMission finishedMission = (FinishedMission) mission;
            if (finishedMission.syncId != null && !finishedMission.syncId.isEmpty()) {
                database.delete(
                        FINISHED_TABLE_NAME,
                        KEY_SYNC_ID + " = ?",
                        new String[]{finishedMission.syncId}
                );
            } else if (mission.storage.isInvalid()) {
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
        ContentValues values = getValuesOfMission(Objects.requireNonNull(mission));
        SQLiteDatabase database = getWritableDatabase();
        String ts = String.valueOf(mission.timestamp);

        int rowsAffected;

        if (mission instanceof FinishedMission) {
            FinishedMission finishedMission = (FinishedMission) mission;
            if (finishedMission.syncId != null && !finishedMission.syncId.isEmpty()) {
                rowsAffected = database.update(
                        FINISHED_TABLE_NAME,
                        values,
                        KEY_SYNC_ID + " = ?",
                        new String[]{finishedMission.syncId}
                );
            } else if (mission.storage.isInvalid()) {
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

    private void ensureDescriptiveMetadata(SQLiteDatabase database) {
        String[] columns = {
                "rowid",
                KEY_PATH,
                KEY_SYNC_ID,
                KEY_DISPLAY_NAME,
                KEY_MIME_TYPE
        };
        try (Cursor cursor = database.query(
                FINISHED_TABLE_NAME,
                columns,
                KEY_SYNC_ID + " = '' OR " + KEY_DISPLAY_NAME + " = '' OR " +
                        KEY_MIME_TYPE + " = ''",
                null,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                String syncId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SYNC_ID));
                String displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(KEY_DISPLAY_NAME)
                );
                String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MIME_TYPE));
                if (syncId == null || syncId.isEmpty()) {
                    values.put(KEY_SYNC_ID, UUID.randomUUID().toString());
                }
                if (displayName == null || displayName.isEmpty()) {
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH));
                    String pathName = Uri.parse(path).getLastPathSegment();
                    values.put(
                            KEY_DISPLAY_NAME,
                            pathName == null || pathName.isEmpty() ? "download" : pathName
                    );
                }
                if (mimeType == null || mimeType.isEmpty()) {
                    values.put(KEY_MIME_TYPE, StoredFileHelper.DEFAULT_MIME);
                }
                if (values.size() > 0) {
                    database.update(
                            FINISHED_TABLE_NAME,
                            values,
                            "rowid = ?",
                            new String[]{
                                    String.valueOf(
                                            cursor.getLong(cursor.getColumnIndexOrThrow("rowid"))
                                    )
                            }
                    );
                }
            }
        }
    }
}
