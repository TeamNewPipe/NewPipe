package org.schabi.newpipe.database.subscription;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.BasicDAO;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.subscription.SubscriptionEntity.CHANNEL_SERVICE_ID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.CHANNEL_TABLE;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.CHANNEL_URL;

@Dao
public interface SubscriptionDAO extends BasicDAO<SubscriptionEntity> {
    @Override
    @Query("SELECT * FROM " + CHANNEL_TABLE)
    Flowable<List<SubscriptionEntity>> findAll();

    @Override
    @Query("SELECT * FROM " + CHANNEL_TABLE + " WHERE " + CHANNEL_SERVICE_ID + " = :serviceId")
    Flowable<List<SubscriptionEntity>> listByService(int serviceId);

    /* Single entity query should not use flowable in case of empty result */
    /* TODO: make query require service id when */
    @Query("SELECT * FROM " + CHANNEL_TABLE + " WHERE " + CHANNEL_URL + " LIKE :url LIMIT 1")
    SubscriptionEntity findSingle(String url);

    @Query("SELECT * FROM " + CHANNEL_TABLE + " WHERE " +
            CHANNEL_URL + " LIKE :url AND " +
            CHANNEL_SERVICE_ID + " = :serviceId")
    Flowable<List<SubscriptionEntity>> findAll(int serviceId, String url);
}
