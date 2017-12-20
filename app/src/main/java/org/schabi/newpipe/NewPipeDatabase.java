package org.schabi.newpipe;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.annotation.NonNull;

import org.schabi.newpipe.database.AppDatabase;

import static org.schabi.newpipe.database.AppDatabase.DATABASE_NAME;

public final class NewPipeDatabase {

    private static AppDatabase databaseInstance;

    private NewPipeDatabase() {
        //no instance
    }

    public static void init(Context context) {
        databaseInstance = Room.databaseBuilder(context.getApplicationContext(),
                AppDatabase.class, DATABASE_NAME
        ).build();
    }

    @NonNull
    public static AppDatabase getInstance() {
        if (databaseInstance == null) throw new RuntimeException("Database not initialized");

        return databaseInstance;
    }
}
