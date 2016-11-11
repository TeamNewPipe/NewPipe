package org.schabi.newpipe.playList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;

import java.util.Arrays;

public class NewPipeSQLiteHelper extends SQLiteOpenHelper {

    private final String TAG = NewPipeSQLiteHelper.class.getName();
    private static final String DATABASE_NAME = "newpipe.db";
    private static final int DATABASE_VERSION = 3;

    public NewPipeSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    interface PLAYLIST_COLUMNS {
        String _ID = BaseColumns._ID;
        String PLAYLIST_NAME = "name";
        String PLAYLIST_SYSTEM = "system";

        String[] ALL_COLUMNS = new String[] {
            _ID, PLAYLIST_NAME, PLAYLIST_SYSTEM
        };
    }

    interface PLAYLIST_ENTRIES_COLUMNS {
        String _ID = BaseColumns._ID;
        String TITLE = "title";
        String DURATION = "duration";
        String SERVICE_ID = "service_id";
        String ID = "id";
        String UPLOADER = "uploader";
        String THUMBNAIL_URL = "thumbnail_url";
        String WEBPAGE_URL = "webpage_url";
        String UPLOAD_DATE = "upload_date";
        String VIEW_COUNT = "view_count";

        String[] ALL_COLUMNS = new String[]{
                _ID, TITLE, DURATION, SERVICE_ID, ID, UPLOADER, THUMBNAIL_URL,
                WEBPAGE_URL, UPLOAD_DATE, VIEW_COUNT
        };
    }

    public interface PLAYLIST_LINK_ENTRIES {
        String PLAYLIST_ID = "playlist_id";
        String PLAYLIST_ENTRIES_ID = "playlist_entries_id";
        String POSITION = "position";

        String[] ALL_COLUMNS = new String[] { PLAYLIST_ID, PLAYLIST_ENTRIES_ID, POSITION};
    }

    interface Tables {
        String PLAYLIST = "playlist";
        String PLAYLIST_LINK_ENTRIES = "playlist_link_entries";
        String PLAYLIST_ENTRIES = "playlist_entries";

        String PLAYLIST_JOIN_PLAYLIST_ENTRIES = Tables.PLAYLIST +
        " INNER JOIN " + Tables.PLAYLIST_LINK_ENTRIES + " ON " + Qualified.PLAYLIST_ID + "=" + Qualified.LINK_ENTRIES_PLAYLIST_ID +
        " INNER JOIN " + Tables.PLAYLIST_ENTRIES + " ON " + Qualified.PLAYLIST_ENTRIES_ID + "=" + Qualified.LINK_ENTRIES_ENTRIES_ID;

        String PLAYLIST_LINK_JOIN_ENTRIES = Tables.PLAYLIST_LINK_ENTRIES +
        " INNER JOIN " + Tables.PLAYLIST_ENTRIES + " ON " + Qualified.PLAYLIST_ENTRIES_ID + "=" + Qualified.LINK_ENTRIES_ENTRIES_ID;
    }

    public interface Qualified {
        String PLAYLIST_ID = Tables.PLAYLIST + "." + PLAYLIST_COLUMNS._ID;
        String PLAYLIST_ENTRIES_ID = Tables.PLAYLIST_ENTRIES + "." + PLAYLIST_ENTRIES_COLUMNS._ID;
        String LINK_ENTRIES_PLAYLIST_ID = Tables.PLAYLIST_LINK_ENTRIES + "." + PLAYLIST_LINK_ENTRIES.PLAYLIST_ID;
        String LINK_ENTRIES_ENTRIES_ID = Tables.PLAYLIST_LINK_ENTRIES + "." + PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID;
    }

    private interface Index {
        String LINK_ENTRIES_PLAYLIST_ENTRIES = "link_entries_playlist_entries_idx";
        String LINK_ENTRIES_PLAYLIST = "link_entries_playlist_idx";
        String ENTRIES_SERVICES_ID_ID = "entries_services_id_id_idx";
    };

    private interface Triggers {
        String PLAYLIST_PLAYLIST_ENTRIES_DELETE = "playlist_playlist_entries_delete";
        String PLAYLIST_LINK_ENTRIES_DELETE = "playlist_link_entries_delete";
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        // 1. Create playlist table
        final String CREATE_TABLE_PLAYLIST = "CREATE TABLE " + Tables.PLAYLIST + " (" +
                PLAYLIST_COLUMNS._ID +   " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PLAYLIST_COLUMNS.PLAYLIST_NAME + " TEXT NOT NULL," +
                PLAYLIST_COLUMNS.PLAYLIST_SYSTEM + " INTEGER NOT NULL DEFAULT 0," +
                "UNIQUE (" + PLAYLIST_COLUMNS.PLAYLIST_NAME + ") ON CONFLICT IGNORE" +
            ");";
        db.execSQL(CREATE_TABLE_PLAYLIST);
        // 2. Create PlayList Entry table
        final String CREATE_TABLE_PLAYLIST_ENTRIES = "CREATE TABLE " + Tables.PLAYLIST_ENTRIES + " (" +
                PLAYLIST_ENTRIES_COLUMNS._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                PLAYLIST_ENTRIES_COLUMNS.DURATION + " INTEGER NOT NULL, " +
                PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID + " INTEGER NOT NULL, " +
                PLAYLIST_ENTRIES_COLUMNS.ID + " TEXT NOT NULL, " +
                PLAYLIST_ENTRIES_COLUMNS.TITLE + " TEXT NOT NULL, " +
                PLAYLIST_ENTRIES_COLUMNS.UPLOADER + " TEXT, " +
                PLAYLIST_ENTRIES_COLUMNS.THUMBNAIL_URL + " TEXT NOT NULL, " +
                PLAYLIST_ENTRIES_COLUMNS.WEBPAGE_URL + " TEXT NOT NULL, "+
                PLAYLIST_ENTRIES_COLUMNS.UPLOAD_DATE + " TEXT, " +
                PLAYLIST_ENTRIES_COLUMNS.VIEW_COUNT + " INTEGER, " +
                "UNIQUE (" + PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID + "," + PLAYLIST_ENTRIES_COLUMNS.ID + ") ON CONFLICT IGNORE" +
            ");";
        db.execSQL(CREATE_TABLE_PLAYLIST_ENTRIES);

        final String CREATE_TABLE_PLAYLIST_LINK_ENTRIES = "CREATE TABLE " + Tables.PLAYLIST_LINK_ENTRIES + " (" +
            PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + " INTEGER NOT NULL," +
            PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID + " INTEGER NOT NULL," +
            PLAYLIST_LINK_ENTRIES.POSITION + " INTEGER NOT NULL," +
            "FOREIGN KEY(" + PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + ") REFERENCES " + Tables.PLAYLIST + "(" + PLAYLIST_COLUMNS._ID + "), " +
            "FOREIGN KEY(" + PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID + ") REFERENCES " + Tables.PLAYLIST_LINK_ENTRIES + "(" + PLAYLIST_ENTRIES_COLUMNS._ID + ")" +
        ");";
        db.execSQL(CREATE_TABLE_PLAYLIST_LINK_ENTRIES);
        // create index for position on playlist search
        final String CREATE_INDEX_PLAYLIST_LINK_ENTRIES_NEXT_TRACK = "CREATE INDEX " + Index.LINK_ENTRIES_PLAYLIST_ENTRIES + " ON " + Tables.PLAYLIST_LINK_ENTRIES + "(" + PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "," + PLAYLIST_LINK_ENTRIES.POSITION + ");";
        db.execSQL(CREATE_INDEX_PLAYLIST_LINK_ENTRIES_NEXT_TRACK);
        // create index for playlist on count item search
        final String CREATE_INDEX_PLAYLIST_LINK_ENTRIES_PLAYLIST_ID = "CREATE INDEX " + Index.LINK_ENTRIES_PLAYLIST + " ON " + Tables.PLAYLIST_LINK_ENTRIES + "(" + PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + ");";
        db.execSQL(CREATE_INDEX_PLAYLIST_LINK_ENTRIES_PLAYLIST_ID);
        // create index for entries with service and id
        final String CREATE_INDEX_PLAYLIST_ENTRIES_PLAYLIST_ID_AND_SERVICE_ID = "CREATE INDEX " + Index.ENTRIES_SERVICES_ID_ID + " ON " + Tables.PLAYLIST_ENTRIES + "(" + PLAYLIST_ENTRIES_COLUMNS.ID + "," + PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID + ");";
        db.execSQL(CREATE_INDEX_PLAYLIST_ENTRIES_PLAYLIST_ID_AND_SERVICE_ID);

        // 3. Create Trigger for delete playlist entries when playlist a playlist is delete
        final String CREATE_TRIGGER_DELETE_PLAYLIST = "CREATE TRIGGER " + Triggers.PLAYLIST_PLAYLIST_ENTRIES_DELETE
                + " BEFORE DELETE ON " + Tables.PLAYLIST + " BEGIN DELETE FROM " + Tables.PLAYLIST_LINK_ENTRIES + " "
                + " WHERE " + Qualified.LINK_ENTRIES_PLAYLIST_ID + "=old." + PLAYLIST_COLUMNS._ID
                + "; END;";
        db.execSQL(CREATE_TRIGGER_DELETE_PLAYLIST);

        // 3. Remove not use youtube id
        final String CREATE_TRIGGER_DELETE_ENTRIES_PLAYLIST =
                "CREATE TRIGGER " + Triggers.PLAYLIST_LINK_ENTRIES_DELETE
                + " BEFORE DELETE ON " + Tables.PLAYLIST_LINK_ENTRIES + " BEGIN "
                + " DELETE FROM " + Tables.PLAYLIST_ENTRIES + " WHERE " + PLAYLIST_ENTRIES_COLUMNS._ID
                + " NOT IN (SELECT " + Qualified.LINK_ENTRIES_ENTRIES_ID + " FROM " + Tables.PLAYLIST_LINK_ENTRIES + ")"
                + "; END;";
        db.execSQL(CREATE_TRIGGER_DELETE_ENTRIES_PLAYLIST);

        db.execSQL(createPlayListSystem(PLAYLIST_SYSTEM.HISTORIC_ID, PLAYLIST_SYSTEM.HISTORIC));
        db.execSQL(createPlayListSystem(PLAYLIST_SYSTEM.FAVORITES_ID, PLAYLIST_SYSTEM.FAVORITES));
        db.execSQL(createPlayListSystem(PLAYLIST_SYSTEM.RELATED_STREAM_ID, PLAYLIST_SYSTEM.RELATED_STREAM));

    }

    private String createPlayListSystem(int id, String name) {
        return "INSERT OR REPLACE INTO " + Tables.PLAYLIST + " (" + PLAYLIST_COLUMNS._ID + "," + PLAYLIST_COLUMNS.PLAYLIST_NAME + "," + PLAYLIST_COLUMNS.PLAYLIST_SYSTEM + ") VALUES (" + id + ",'" + name + "', 1);";
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.PLAYLIST_LINK_ENTRIES_DELETE);
        db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.PLAYLIST_PLAYLIST_ENTRIES_DELETE);
        db.execSQL("DROP INDEX IF EXISTS " + Index.LINK_ENTRIES_PLAYLIST_ENTRIES);
        db.execSQL("DROP INDEX IF EXISTS " + Index.LINK_ENTRIES_PLAYLIST);
        db.execSQL("DROP INDEX IF EXISTS " + Index.ENTRIES_SERVICES_ID_ID);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PLAYLIST_LINK_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PLAYLIST_ENTRIES);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PLAYLIST);
        onCreate(db);
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
