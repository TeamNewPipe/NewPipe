package org.schabi.newpipe;

import android.arch.persistence.room.Room;
import android.content.Context;
import android.support.annotation.NonNull;

import org.schabi.newpipe.auth.AuthService;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.Database;
import org.schabi.newpipe.database.RemoteDatabaseImpl;

import static org.schabi.newpipe.database.AppDatabase.DATABASE_NAME;
import static org.schabi.newpipe.database.Migrations.MIGRATION_11_12;
import static org.schabi.newpipe.database.Migrations.MIGRATION_12_13;

public final class NewPipeDatabase {

    private static volatile Database remoteDatabaseInstance;
    private static volatile Database localDatabaseInstance;

    private NewPipeDatabase() {
        //no instance
    }

    private static Database getRemoteDatabase(AppDatabase roomDb, Context context) {
        return new RemoteDatabaseImpl(roomDb, context);
    }

    private static Database getLocalDatabase(Context context) {
        return Room
                .databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build();
    }

    @NonNull
    private static Database getRemoteInstance(@NonNull Context context) {
        AppDatabase roomDb = (AppDatabase) getLocalInstance(context);
        if (remoteDatabaseInstance == null) {
            synchronized (NewPipeDatabase.class) {
                if (remoteDatabaseInstance == null) {
                    remoteDatabaseInstance = getRemoteDatabase(roomDb, context);
                }
            }
        }

        return remoteDatabaseInstance;
    }

    @NonNull
    private static Database getLocalInstance(@NonNull Context context) {
        if (localDatabaseInstance == null) {
            synchronized (NewPipeDatabase.class) {
                if (localDatabaseInstance == null) {
                    localDatabaseInstance = getLocalDatabase(context);
                }
            }
        }

        return localDatabaseInstance;
    }

    @NonNull
    public static Database getInstance(@NonNull Context context) {
        boolean loggedIn = AuthService.getInstance(context).isLoggedIn();
        if(loggedIn){
            return getRemoteInstance(context);
        }else {
            return getLocalInstance(context);
        }
    }

}
