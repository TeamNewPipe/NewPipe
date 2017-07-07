package org.schabi.newpipe.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.database.video.VideoDAO;
import org.schabi.newpipe.database.video.VideoEntity;

@Database(entities = {SubscriptionEntity.class, VideoEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase{

    public static final String DATABASE_NAME = "basic.db";

    public abstract SubscriptionDAO subscriptionDAO();
    public abstract VideoDAO videoDAO();
}
