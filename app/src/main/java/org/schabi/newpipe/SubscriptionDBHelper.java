package org.schabi.newpipe;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SubscriptionDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Subscriptions.db";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Subscription.Entry.TABLE_NAME + " (" +
                    "Name" + " TEXT," +
                    "Link" + " TEXT," +
                    "Avatar" + " TEXT)";


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
}
