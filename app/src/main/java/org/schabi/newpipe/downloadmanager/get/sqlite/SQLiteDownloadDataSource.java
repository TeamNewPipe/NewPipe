package org.schabi.newpipe.downloadmanager.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.schabi.newpipe.downloadmanager.get.DownloadDataSource;
import org.schabi.newpipe.downloadmanager.get.DownloadMission;
import org.schabi.newpipe.downloadmanager.get.sqlite.DownloadMissionSQLiteHelper;

import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.downloadmanager.get.sqlite.DownloadMissionSQLiteHelper.KEY_LOCATION;
import static org.schabi.newpipe.downloadmanager.get.sqlite.DownloadMissionSQLiteHelper.KEY_NAME;
import static org.schabi.newpipe.downloadmanager.get.sqlite.DownloadMissionSQLiteHelper.MISSIONS_TABLE_NAME;


/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Non-thread-safe implementation of {@link DownloadDataSource}
 */
public class SQLiteDownloadDataSource implements DownloadDataSource {

    private static final String TAG = "DownloadDataSourceImpl";
    private final DownloadMissionSQLiteHelper downloadMissionSQLiteHelper;

    public SQLiteDownloadDataSource(Context context) {
        downloadMissionSQLiteHelper = new DownloadMissionSQLiteHelper(context);
    }

    @Override
    public List<DownloadMission> loadMissions() {
        ArrayList<DownloadMission> result;
        SQLiteDatabase database = downloadMissionSQLiteHelper.getReadableDatabase();
        Cursor cursor = database.query(MISSIONS_TABLE_NAME, null, null,
                null, null, null, DownloadMissionSQLiteHelper.KEY_TIMESTAMP);

        int count = cursor.getCount();
        if (count == 0) return new ArrayList<>();
        result = new ArrayList<>(count);
        while (cursor.moveToNext()) {
            result.add(DownloadMissionSQLiteHelper.getMissionFromCursor(cursor));
        }
        return result;
    }

    @Override
    public void addMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionSQLiteHelper.getWritableDatabase();
        ContentValues values = DownloadMissionSQLiteHelper.getValuesOfMission(downloadMission);
        database.insert(MISSIONS_TABLE_NAME, null, values);
    }

    @Override
    public void updateMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionSQLiteHelper.getWritableDatabase();
        ContentValues values = DownloadMissionSQLiteHelper.getValuesOfMission(downloadMission);
        String whereClause = KEY_LOCATION + " = ? AND " +
                KEY_NAME + " = ?";
        int rowsAffected = database.update(MISSIONS_TABLE_NAME, values,
                whereClause, new String[]{downloadMission.location, downloadMission.name});
        if (rowsAffected != 1) {
            Log.e(TAG, "Expected 1 row to be affected by update but got " + rowsAffected);
        }
    }

    @Override
    public void deleteMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionSQLiteHelper.getWritableDatabase();
        database.delete(MISSIONS_TABLE_NAME,
                KEY_LOCATION + " = ? AND " +
                        KEY_NAME + " = ?",
                new String[]{downloadMission.location, downloadMission.name});
    }
}
