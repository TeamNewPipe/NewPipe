package org.schabi.newpipe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import org.schabi.newpipe.database.AppDatabase;

import static org.schabi.newpipe.database.AppDatabase.DATABASE_NAME;
import static org.schabi.newpipe.database.Migrations.MIGRATION_1_2;
import static org.schabi.newpipe.database.Migrations.MIGRATION_2_3;

public final class NewPipeDatabase {
    private static volatile AppDatabase databaseInstance;

    private NewPipeDatabase() {
        //no instance
    }

    private static AppDatabase getDatabase(final Context context) {
        return Room
                .databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build();
    }

    @NonNull
    public static AppDatabase getInstance(@NonNull final Context context) {
        AppDatabase result = databaseInstance;
        if (result == null) {
            synchronized (NewPipeDatabase.class) {
                result = databaseInstance;
                if (result == null) {
                    databaseInstance = getDatabase(context);
                    result = databaseInstance;
                }
            }
        }

        return result;
    }
}
