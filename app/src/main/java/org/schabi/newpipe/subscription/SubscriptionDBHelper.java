package org.schabi.newpipe.subscription;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "subscriptions.db";

    SQLiteDatabase db;

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Subscription.Entry.TABLE_NAME + " (" +
                    Subscription.Entry.COLUMN_SERVICE_ID + " INTEGER," +
                    Subscription.Entry.COLUMN_NAME + " TEXT," +
                    Subscription.Entry.COLUMN_LINK + " TEXT," +
                    Subscription.Entry.COLUMN_AVATAR + " TEXT)";


    // add version?
    SubscriptionDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    List<SubscribedChannelInfo> read(Context context){
        SQLiteDatabase db = new SubscriptionDBHelper(context).getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * from " + Subscription.Entry.TABLE_NAME, null);

        List<SubscribedChannelInfo> infoList = new ArrayList<>();
        while(cursor.moveToNext()) {
            int serviceID = cursor.getInt(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_SERVICE_ID));
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_NAME));
            String link = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_LINK));
            String avatar = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_AVATAR));
            SubscribedChannelInfo subscribedChannelInfo = new SubscribedChannelInfo(serviceID, name, link, avatar);
            infoList.add(subscribedChannelInfo);
        }

        cursor.close();
        return infoList;
    }

    public static void write(Activity activity, StreamInfo streamInfo){
        SQLiteDatabase db = new SubscriptionDBHelper(activity).getWritableDatabase();

        int serviceID = streamInfo.service_id;
        String name = streamInfo.uploader;
        String link = streamInfo.channel_url;
        String avatar = streamInfo.uploader_thumbnail_url;

        ContentValues values = new ContentValues();

        values.put(Subscription.Entry.COLUMN_SERVICE_ID, serviceID);
        values.put(Subscription.Entry.COLUMN_NAME, name);
        values.put(Subscription.Entry.COLUMN_LINK, link);
        values.put(Subscription.Entry.COLUMN_AVATAR, avatar);

        db.insert(Subscription.Entry.TABLE_NAME, null, values);
    }

}
