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
import java.util.List;

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
        String QUEUE = "queue";
        int QUEUE_ID = -5;
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
        if(database != null && database.isOpen()) {
            dbHelper.close();
        }
    }

    public boolean hasPersonalPlayList() {
        open();
        long nb = DatabaseUtils.queryNumEntries(database, Tables.PLAYLIST, PLAYLIST_COLUMNS.PLAYLIST_SYSTEM + "=?", new String[]{ "0" });
        close();
        return nb > 0;
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

    public int getPlayListId(final String name) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST,
                new String[]{PLAYLIST_COLUMNS._ID},
                PLAYLIST_COLUMNS.PLAYLIST_NAME + "=?",
                new String[]{name},
                null, null, null);
        int id = -1;
        if(cursor.getCount() != 0) {
            cursor.moveToFirst();
            id = cursor.getInt(0);
        }
        cursor.close();
        close();
        return id;
    }


    public StreamPreviewInfo getRandomItem(int playlistId) {
        open();
        Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{PLAYLIST_LINK_ENTRIES.POSITION}),
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?",
                new String[]{String.valueOf(playlistId)},
                null,
                null,
                null,
                "RANDOM() LIMIT 1");
        final StreamPreviewInfo info = cursor.getCount() > 0 ? getStreamPreviewInfo(cursor) : null;
        cursor.close();
        close();
        return info;
    }

    public StreamPreviewInfo getPreviousEntryForItems(final int playlistId, final int position) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{PLAYLIST_LINK_ENTRIES.POSITION}),
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.POSITION + "<?",
                new String[]{
                    String.valueOf(playlistId),
                    String.valueOf(position)
                },
                null,
                null,
                PLAYLIST_LINK_ENTRIES.POSITION + " DESC", "1");
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
    public StreamPreviewInfo getNextEntryForItems(final int playlistId, final int position) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{PLAYLIST_LINK_ENTRIES.POSITION}),
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.POSITION + ">?",
                new String[]{
                    String.valueOf(playlistId),
                    String.valueOf(position)
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

    public StreamPreviewInfo getEntryForItems(final int playlistId, final int position) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{PLAYLIST_LINK_ENTRIES.POSITION}),
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.POSITION + "=?",
                new String[]{
                    String.valueOf(playlistId),
                    String.valueOf(position)
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

    private StreamPreviewInfo getEntryForPlayList(final int playlistId, final boolean orderAsc) {
        open();
        final String ORDER = orderAsc ? " ASC" : " DESC";
        final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{PLAYLIST_LINK_ENTRIES.POSITION}),
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?", new String[]{ String.valueOf(playlistId)}, null, null,
                PLAYLIST_LINK_ENTRIES.POSITION + ORDER, "1");
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

    public StreamPreviewInfo getFirstEntryForPlayList(final int playlistId) {
        return getEntryForPlayList(playlistId, true);
    }

    public StreamPreviewInfo getLastEntryForPlayList(final int playlistId) {
        return getEntryForPlayList(playlistId, false);
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

    public void deletePlayList(int id) {
        open();
        // For entry it's delete by the trigger
        Log.i(TAG, "Delete playlist with id : " + id);
        database.delete(Tables.PLAYLIST, PLAYLIST_COLUMNS._ID + "=?", new String[]{String.valueOf(id)});
        Log.i(TAG, "Deleted playlist with id : " + id);
        close();
    }

    public long getNumberOfEntriesOnPlayList(final int playlist_id) {
        open();
        long nb = DatabaseUtils.queryNumEntries(database, Tables.PLAYLIST_LINK_ENTRIES, PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?", new String[]{String.valueOf(playlist_id)});
        close();
        return nb;
    }

    public void addEntriesToPlayList(final int playList_id, final List<StreamPreviewInfo> streams) {
        if(streams != null && !streams.isEmpty()) {
            for(int i = 0; i < streams.size(); i++) {
                addEntryToPlayList(playList_id, streams.get(i));
            }
        }
    }

    public long addEntryToPlayList(final int playList_id, final StreamPreviewInfo info) {
        // first add entry
        // first check if values is already existing in data base
        long entries_id = getEntryId(info.id, info.service_id);
        final ContentValues values = new ContentValues();
        if (entries_id < 0) {
            open();
            values.put(PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID, info.service_id);
            values.put(PLAYLIST_ENTRIES_COLUMNS.ID, info.id);
            values.put(PLAYLIST_ENTRIES_COLUMNS.TITLE, info.title);
            values.put(PLAYLIST_ENTRIES_COLUMNS.UPLOADER, info.uploader);
            values.put(PLAYLIST_ENTRIES_COLUMNS.THUMBNAIL_URL, info.thumbnail_url);
            values.put(PLAYLIST_ENTRIES_COLUMNS.WEBPAGE_URL, info.webpage_url);
            values.put(PLAYLIST_ENTRIES_COLUMNS.UPLOAD_DATE, info.upload_date);
            values.put(PLAYLIST_ENTRIES_COLUMNS.VIEW_COUNT, info.view_count);
            values.put(PLAYLIST_ENTRIES_COLUMNS.DURATION, info.duration);
            entries_id = database.insert(Tables.PLAYLIST_ENTRIES, null, values);
        }
        if(entries_id > -1) {
            values.clear();
            values.put(PLAYLIST_LINK_ENTRIES.PLAYLIST_ID, playList_id);
            values.put(PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID, entries_id);
            values.put(PLAYLIST_LINK_ENTRIES.POSITION, getNumberOfEntriesOnPlayList(playList_id) + 1);
            // because count entries on playlist close the database
            open();
            entries_id = database.insert(Tables.PLAYLIST_LINK_ENTRIES, null, values);
        }
        close();
        return entries_id;
    }

    public StreamPreviewInfo getEntryFromPlayList(final int playlistId, final int position) {
            open();
            final Cursor cursor = database.query(Tables.PLAYLIST_LINK_JOIN_ENTRIES,
                    concat(PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS, new String[]{ PLAYLIST_LINK_ENTRIES.POSITION}),
                    PLAYLIST_LINK_ENTRIES.POSITION + "=? AND " +
                    PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=?",
                    new String[]{String.valueOf(position), String.valueOf(playlistId) },
                    null,
                    null,
                    "1");
            StreamPreviewInfo entry;
            if(cursor.getCount() > 0) {
                cursor.moveToFirst();
                entry = getStreamPreviewInfo(cursor);
            } else  {
                entry = null;
            }
            cursor.close();
            close();
            return entry;

    }

    public long getEntryId(final String id, final int service_id) {
        open();
        final Cursor cursor = database.query(Tables.PLAYLIST_ENTRIES,
                new String[]{Qualified.PLAYLIST_ENTRIES_ID},
                PLAYLIST_ENTRIES_COLUMNS.ID + "=? AND " + PLAYLIST_ENTRIES_COLUMNS.SERVICE_ID + "=?",
                new String[]{id, String.valueOf(service_id)}, null, null, null);
        long entries_id;
        if(cursor.getCount() > 0) {
            cursor.moveToFirst();
            entries_id = cursor.getLong(0);
        } else  {
            entries_id = -1;
        }
        cursor.close();
        close();
        return entries_id;
    }

    public int deleteEntryFromPlayList(int playlist_id, int position) {
        open();
        final int result = database.delete(Tables.PLAYLIST_LINK_ENTRIES,
                PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                PLAYLIST_LINK_ENTRIES.POSITION + "=?",
                new String[]{String.valueOf(playlist_id), String.valueOf(position)});
        Log.i(TAG, String.format("Deleted playlist entry with position : %d for playlist : %d", position, playlist_id));
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
        final String[] ALL_COLUMNS = concat(concat(PLAYLIST_COLUMNS.ALL_COLUMNS, PLAYLIST_ENTRIES_COLUMNS.ALL_COLUMNS), new String[]{ PLAYLIST_LINK_ENTRIES.POSITION});
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

    public void updatePosition(int playListId, StreamPreviewInfo stream, int newPosition) {
        final long entryId = getEntryId(stream.id, stream.service_id);
        final long oldPosition = stream.position;
        open();
        final ContentValues cv = new ContentValues();
        cv.put(PLAYLIST_LINK_ENTRIES.POSITION, newPosition);
        database.update(Tables.PLAYLIST_LINK_ENTRIES, cv,
                        PLAYLIST_LINK_ENTRIES.PLAYLIST_ID + "=? AND " +
                        PLAYLIST_LINK_ENTRIES.POSITION + "=? AND " +
                        PLAYLIST_LINK_ENTRIES.PLAYLIST_ENTRIES_ID + "=?",
                new String[]{
                        String.valueOf(playListId),
                        String.valueOf(oldPosition),
                        String.valueOf(entryId)
                });
        Log.d(TAG, String.format("Update position of entriesId %d on playlistId %d: %d -> %d", entryId,
                playListId, oldPosition, newPosition));
        // update to new position item
        stream.position = newPosition;
        close();
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
        final int positionColumn = cursor.getColumnIndex(PLAYLIST_LINK_ENTRIES.POSITION);
        if(positionColumn > -1) {
            stream.position = cursor.getInt(positionColumn);
        }
        return stream;
    }

    private PlayList cursorToPlayList(final Cursor cursor) {
        final int _id = cursor.getInt(cursor.getColumnIndex(PLAYLIST_COLUMNS._ID));
        final String playlistName = cursor.getString(cursor.getColumnIndex(PLAYLIST_COLUMNS.PLAYLIST_NAME));
        return new PlayList(_id, playlistName, new ArrayList<StreamPreviewInfo>());
    }

}
