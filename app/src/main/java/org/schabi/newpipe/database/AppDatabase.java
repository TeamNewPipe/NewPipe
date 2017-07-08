package org.schabi.newpipe.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

@Database(entities = {SubscriptionEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase{

    public static final String DATABASE_NAME = "newpipe.db";

    public abstract SubscriptionDAO subscriptionDAO();
}
