package org.schabi.newpipe.subscription;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

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

    static void read(Context context){
        SQLiteDatabase db = new SubscriptionDBHelper(context, null).getReadableDatabase();

        String[] projection = {
                //Subscription.Entry.COLUMN_NAME,
                Subscription.Entry.COLUMN_LINK,
                //Subscription.Entry.COLUMN_AVATAR
        };

        String selection = Subscription.Entry._ID + " = ?";
        String[] selectionArgs = { "*" };

       /* Cursor cursor = db.query(
                Subscription.Entry.TABLE_NAME,            // The table to query
                projection,                               // The columns to return
                selection,                                // The columns for the WHERE clause
                selectionArgs,                            // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                null                                      // The sort order
        );
*/
        Cursor kursor = db.rawQuery("SELECT * from " + Subscription.Entry.TABLE_NAME, null);

        List<String> names = new ArrayList<>();
        while(kursor.moveToNext()) {
            String itemId = kursor.getString(
                    kursor.getColumnIndexOrThrow(Subscription.Entry.COLUMN_NAME));
            names.add(itemId);
        }
        kursor.close();
        String test = names.get(1);
        Toast.makeText(context, test, Toast.LENGTH_SHORT).show();
    }
}
