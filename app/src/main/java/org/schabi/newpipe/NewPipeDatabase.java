package org.schabi.newpipe;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.annotation.NonNull;

import org.schabi.newpipe.database.AppDatabase;

import static org.schabi.newpipe.database.AppDatabase.DATABASE_NAME;

public class NewPipeDatabase {

    private static AppDatabase sInstance;

    // For Singleton instantiation
    private static final Object LOCK = new Object();

    @NonNull
    public synchronized static AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {

                    sInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).build();
                }
            }
        }
        return sInstance;
    }
}
