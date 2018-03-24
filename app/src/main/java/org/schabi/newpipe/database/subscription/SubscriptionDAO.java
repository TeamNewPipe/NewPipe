package org.schabi.newpipe.database.subscription;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_SERVICE_ID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_TABLE;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_UID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_URL;

@Dao
public abstract class SubscriptionDAO implements BasicDAO<SubscriptionEntity> {
    @Override
    @Query("SELECT * FROM " + SUBSCRIPTION_TABLE)
    public abstract Flowable<List<SubscriptionEntity>> getAll();

    @Override
    @Query("DELETE FROM " + SUBSCRIPTION_TABLE)
    public abstract int deleteAll();

    @Override
    @Query("SELECT * FROM " + SUBSCRIPTION_TABLE + " WHERE " + SUBSCRIPTION_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<SubscriptionEntity>> listByService(int serviceId);

    @Query("SELECT * FROM " + SUBSCRIPTION_TABLE + " WHERE " +
            SUBSCRIPTION_URL + " LIKE :url AND " +
            SUBSCRIPTION_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<SubscriptionEntity>> getSubscription(int serviceId, String url);

    @Query("SELECT " + SUBSCRIPTION_UID + " FROM " + SUBSCRIPTION_TABLE + " WHERE " +
            SUBSCRIPTION_URL + " LIKE :url AND " +
            SUBSCRIPTION_SERVICE_ID + " = :serviceId")
    abstract Long getSubscriptionIdInternal(int serviceId, String url);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract Long insertInternal(final SubscriptionEntity entities);

    @Transaction
    public List<SubscriptionEntity> upsertAll(List<SubscriptionEntity> entities) {
        for (SubscriptionEntity entity : entities) {
            Long uid = insertInternal(entity);

            if (uid != -1) {
                entity.setUid(uid);
                continue;
            }

            uid = getSubscriptionIdInternal(entity.getServiceId(), entity.getUrl());
            entity.setUid(uid);

            if (uid == -1) {
                throw new IllegalStateException("Invalid subscription id (-1)");
            }

            update(entity);
        }

        return entities;
    }
}
