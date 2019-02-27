package us.shandian.giga.get.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;

import static us.shandian.giga.get.sqlite.DownloadMissionHelper.KEY_LOCATION;
import static us.shandian.giga.get.sqlite.DownloadMissionHelper.KEY_NAME;
import static us.shandian.giga.get.sqlite.DownloadMissionHelper.MISSIONS_TABLE_NAME;

public class DownloadDataSource {

    private static final String TAG = "DownloadDataSource";
    private final DownloadMissionHelper downloadMissionHelper;

    public DownloadDataSource(Context context) {
        downloadMissionHelper = new DownloadMissionHelper(context);
    }

    public ArrayList<FinishedMission> loadFinishedMissions() {
        SQLiteDatabase database = downloadMissionHelper.getReadableDatabase();
        Cursor cursor = database.query(MISSIONS_TABLE_NAME, null, null,
                null, null, null, DownloadMissionHelper.KEY_TIMESTAMP);

        int count = cursor.getCount();
        if (count == 0) return new ArrayList<>(1);

        ArrayList<FinishedMission> result = new ArrayList<>(count);
        while (cursor.moveToNext()) {
            result.add(DownloadMissionHelper.getMissionFromCursor(cursor));
        }

        return result;
    }

    public void addMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionHelper.getWritableDatabase();
        ContentValues values = DownloadMissionHelper.getValuesOfMission(downloadMission);
        database.insert(MISSIONS_TABLE_NAME, null, values);
    }

    public void deleteMission(Mission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionHelper.getWritableDatabase();
        database.delete(MISSIONS_TABLE_NAME,
                KEY_LOCATION + " = ? AND " +
                        KEY_NAME + " = ?",
                new String[]{downloadMission.location, downloadMission.name});
    }

    public void updateMission(DownloadMission downloadMission) {
        if (downloadMission == null) throw new NullPointerException("downloadMission is null");
        SQLiteDatabase database = downloadMissionHelper.getWritableDatabase();
        ContentValues values = DownloadMissionHelper.getValuesOfMission(downloadMission);
        String whereClause = KEY_LOCATION + " = ? AND " +
                KEY_NAME + " = ?";
        int rowsAffected = database.update(MISSIONS_TABLE_NAME, values,
                whereClause, new String[]{downloadMission.location, downloadMission.name});
        if (rowsAffected != 1) {
            Log.e(TAG, "Expected 1 row to be affected by update but got " + rowsAffected);
        }
    }
}
