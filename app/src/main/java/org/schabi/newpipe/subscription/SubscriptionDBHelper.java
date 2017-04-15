package org.schabi.newpipe.subscription;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Subscriptions.db";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Subscription.Entry.TABLE_NAME + " (" +
                    Subscription.Entry.COLUMN_NAME + " TEXT," +
                    Subscription.Entry.COLUMN_LINK + " TEXT," +
                    Subscription.Entry.COLUMN_AVATAR + " TEXT)";


    // add version?
    public SubscriptionDBHelper(Context context, SQLiteDatabase.CursorFactory factory) {
        super(context, DATABASE_NAME, factory, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static List<SubscribedChannelInfo> read(Context context){
        SQLiteDatabase db = new SubscriptionDBHelper(context, null).getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * from " + Subscription.Entry.TABLE_NAME, null);

        List<SubscribedChannelInfo> infoList = new ArrayList<>();
        while(cursor.moveToNext()) {
            String name = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_NAME));
            String link = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_LINK));
            String avatar = cursor.getString(
                    cursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_AVATAR));
            SubscribedChannelInfo subscribedChannelInfo = new SubscribedChannelInfo(name, link, avatar);
            infoList.add(subscribedChannelInfo);
        }
        cursor.close();
        return infoList;
    }
}
