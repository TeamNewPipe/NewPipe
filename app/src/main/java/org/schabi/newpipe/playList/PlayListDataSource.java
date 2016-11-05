package org.schabi.newpipe.playList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.playList.NewPipeSQLiteHelper.PLAYLIST_COLUMNS;
import org.schabi.newpipe.playList.NewPipeSQLiteHelper.PLAYLIST_LINK_ENTRIES;

import java.util.ArrayList;

import static org.schabi.newpipe.playList.NewPipeSQLiteHelper.PLAYLIST_ENTRIES_COLUMNS;
import static org.schabi.newpipe.playList.NewPipeSQLiteHelper.Qualified;
import static org.schabi.newpipe.playList.NewPipeSQLiteHelper.Tables;
import static org.schabi.newpipe.playList.NewPipeSQLiteHelper.concat;


public class PlayListDataSource {

    private final String TAG = PlayListDataSource.class.getName();

    private SQLiteDatabase database;
    private NewPipeSQLiteHelper dbHelper;

    public interface PLAYLIST_SYSTEM {
        int POSITION_DEFAULT = 0;
        int NOT_IN_PLAYLIST_ID = -1;
        String HISTORIC = "historic";
        int HISTORIC_ID = -2;
        String RELATED_STREAM = "related_stream";
        int RELATED_STREAM_ID = -3;
        String FAVORITES = "favorites";
        int FAVORITES_ID = -4;
    }

    public PlayListDataSource(final Context context) {
        dbHelper = new NewPipeSQLiteHelper(context);
    }

    private void open() {
        if(database == null || !database.isOpen()) {
            database = dbHelper.getWritableDatabase();
        }
    }

    private void close() {
        dbHelper.close();
    }

    public String getPlaylistName(final int playlistId) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST,
                new String[]{PLAYLIST_COLUMNS.PLAYLIST_NAME},
                PLAYLIST_COLUMNS._ID + "=?",
                new String[]{String.valueOf(playlistId)},
                null, null, null);
        cursor.moveToFirst();
        final String name = cursor.getString(cursor.getColumnIndex(PLAYLIST_COLUMNS.PLAYLIST_NAME));
        cursor.close();
        close();
        return name;
    }

    public StreamPreviewInfo getNextEntriesForItems(final int currentPlayList, final int currentPosition) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
            NewPipeSQLiteHelper.PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS,
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.POSITION + ">?",
                new String[]{
                    String.valueOf(currentPlayList),
                    String.valueOf(currentPosition)
                }, null, null,
                PLAYLIST_LINK_ENTRIES.POSITION + " ASC", "1");
        final StreamPreviewInfo stream;
        if(cursor.getCount() > 0) {
            cursor.moveToFirst();
            stream = getStreamPreviewInfo(cursor);
        } else {
            stream = null;
        }
        cursor.close();
        close();
        return stream;
    }

    public PlayList createPlayList(final String name) {
        open();
        final ContentValues values = new ContentValues();
        values.put(PLAYLIST_COLUMNS.PLAYLIST_NAME, name);
        values.put(PLAYLIST_COLUMNS.PLAYLIST_SYSTEM, 0);
        final long insertId = database.insert(Tables.PLAYLIST, null, values);
        final Cursor cursor = database.query(Tables.PLAYLIST, PLAYLIST_COLUMNS.ALL_COLUMNS, PLAYLIST_COLUMNS._ID + "=?", new String[]{String.valueOf(insertId)}, null, null, null);
        cursor.moveToFirst();
        final PlayList playList = cursorToPlayList(cursor);
        cursor.close();
        close();
        return playList;
    }

    public void deletePlayList(final PlayList playList) {
        final int _id = playList.get_id();
        deletePlayList(_id);
    }

    public void deletePlayList(int _id) {
        open();
        // For entry it's delete by the trigger
        Log.i(TAG, "Delete playlist with _id : " + _id);
        database.delete(Tables.PLAYLIST, PLAYLIST_COLUMNS._ID + "=?", new String[]{String.valueOf(_id)});
        Log.i(TAG, "Deleted playlist with _id : " + _id);
        close();
    }

    public long getNumberOfEntriesOnPlayList(final int playlist_id) {
        open();
        long nb = DatabaseUtils.longForQuery(database, "SELECT COUNT(*) FROM " + Tables.PLAYLIST_LINK_ENTRIES + " WHERE " + PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?", new String[]{String.valueOf(playlist_id)});
        close();
        return nb;
    }

    public long addEntryFromPlayList(final int playList_id, final StreamPreviewInfo info) {
        // first add entry
        open();
        final ContentValues values = new ContentValues();
        values.put(PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID, info.service_id);
        values.put(PLAYLIST_ENTRIES_COLUMNS.ID, info.id);
        values.put(PLAYLIST_ENTRIES_COLUMNS.TITLE, info.title);
        values.put(PLAYLIST_ENTRIES_COLUMNS.UPLOADER, info.uploader);
        values.put(PLAYLIST_ENTRIES_COLUMNS.THUMBNAIL_URL, info.thumbnail_url);
        values.put(PLAYLIST_ENTRIES_COLUMNS.WEBPAGE_URL, info.webpage_url);
        values.put(PLAYLIST_ENTRIES_COLUMNS.UPLOAD_DATE, info.upload_date);
        values.put(PLAYLIST_ENTRIES_COLUMNS.VIEW_COUNT, info.view_count);
        values.put(PLAYLIST_ENTRIES_COLUMNS.DURATION, info.duration);
        long id = database.insert(Tables.PLAYLIST_ENTRIES, null, values);

        values.clear();
        values.put(PLAYLIST_LINK_ENTRIES.PLAYLIST_ID, playList_id);
        values.put(PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID, id);
        values.put(PLAYLIST_LINK_ENTRIES.POSITION, getNumberOfEntriesOnPlayList(playList_id) + 1);

        open();
        id = database.insert(Tables.PLAYLIST_LINK_ENTRIES, null, values);
        close();
        return id;
    }

    public long getEntriesId(final String id) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_ENTRIES,
                new String[]{Qualified.PLAYLIST_ENTRIES_ID},
                PLAYLIST_ENTRIES_COLUMNS.ID + "=?",
                new String[]{id}, null, null, null);
        cursor.moveToFirst();
        long entries_id = cursor.getLong(0);
        cursor.close();
        close();
        return entries_id;
    }

    public int deleteEntryFromPlayList(final int playlist_id, final String id) {
        final long entriesId = getEntriesId(id);
        open();
        Log.i(TAG, "Delete playlist entry with ref_id : " + entriesId + " for playlist : " + playlist_id);
        final int result = database.delete(Tables.PLAYLIST_LINK_ENTRIES,
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID + "=?",
                new String[]{String.valueOf(playlist_id), String.valueOf(entriesId)});
        Log.i(TAG, "Deleted playlist entry with ref_id : " + id + " for playlist : " + playlist_id);
        close();
        return result;
    }

    public int deleteAllEntryFromPlayList(final int playlist_id) {
        open();
        final int result = database.delete(Tables.PLAYLIST_LINK_ENTRIES, PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?", new String[]{String.valueOf(playlist_id)});
        close();
        return result;
    }

    public PlayList getPlayListWithEntries(final int playlist_id, final int page) {
        open();
        final String[] ALL_COLUMNS = concat(PLAYLIST_COLUMNS.ALL_COLUMNS, PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS);
        final ArrayList<String> ALL_COLUMNS_USE = new ArrayList<>(ALL_COLUMNS.length);
        for (final String ALL_COLUMN : ALL_COLUMNS) {
            if (!PLAYLIST_COLUMNS._ID.equals(ALL_COLUMN)) {
                ALL_COLUMNS_USE.add(ALL_COLUMN);
            }
        }
        final String limit = page > 0 ? (page * 10) + ",10" : "10";
        final Cursor cursor = database.query(Tables.PLAYLIST_JOIN_PLAYLIST_ENTRIES,
                ALL_COLUMNS_USE.toArray(new String[ALL_COLUMNS_USE.size()]),
                Qualified.PLAYLIST_ID + "=?",
                new String[]{String.valueOf(playlist_id)},
                null,
                null,
                PLAYLIST_LINK_ENTRIES.POSITION + " ASC",
                limit);
        final ArrayList<StreamPreviewInfo> entries = new ArrayList<>(cursor.getCount());
        String name = null;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            if (name == null) {
                name = cursor.getString(cursor.getColumnIndex(PLAYLIST_COLUMNS.PLAYLIST_NAME));
            }
            entries.add(getStreamPreviewInfo(cursor));
            cursor.moveToNext();
        }
        cursor.close();
        close();
        return new PlayList(playlist_id, name, entries);
    }

    public SparseArray<String> getAllPlayList(final boolean includeSystem) {
        open();
        final SparseArray<String> playList = new SparseArray<>();
        final String selection = includeSystem ? null : PLAYLIST_COLUMNS.PLAYLIST_SYSTEM + "=?";
        final String[] selectionArg = includeSystem ? null : new String[]{"0"};
        final Cursor cursor = database.query(Tables.PLAYLIST, PLAYLIST_COLUMNS.ALL_COLUMNS, selection, selectionArg, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            final String name = cursor.getString(cursor.getColumnIndex(PLAYLIST_COLUMNS.PLAYLIST_NAME));
            final int uid = cursor.getInt(cursor.getColumnIndex(PLAYLIST_COLUMNS._ID));
            playList.put(uid, name);
            cursor.moveToNext();
        }
        cursor.close();
        close();
        return playList;
    }

    public boolean hasNextPage(int playListId, int page) {
        long nbEntries = getNumberOfEntriesOnPlayList(playListId);
        int nbItemViewOnThisPage = page == 0 ? 10 : page * 10;
        return nbEntries - nbItemViewOnThisPage >= 0;
    }

    @NonNull
    private StreamPreviewInfo getStreamPreviewInfo(final Cursor cursor) {
        final StreamPreviewInfo stream = new StreamPreviewInfo();
        stream.service_id = cursor.getInt(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID));
        stream.id = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.ID));
        stream.title = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.TITLE));
        stream.uploader = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.UPLOADER));
        stream.thumbnail_url = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.THUMBNAIL_URL));
        stream.webpage_url = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.WEBPAGE_URL));
        stream.upload_date = cursor.getString(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.UPLOAD_DATE));
        stream.view_count = cursor.getLong(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.VIEW_COUNT));
        stream.duration = cursor.getInt(cursor.getColumnIndex(PLAYLIST_ENTRIES_COLUMNS.DURATION));
        return stream;
    }

    private PlayList cursorToPlayList(final Cursor cursor) {
        final int _id = cursor.getInt(cursor.getColumnIndex(PLAYLIST_COLUMNS._ID));
        final String playlistName = cursor.getString(cursor.getColumnIndex(PLAYLIST_COLUMNS.PLAYLIST_NAME));
        return new PlayList(_id, playlistName, new ArrayList<StreamPreviewInfo>());
    }

}
