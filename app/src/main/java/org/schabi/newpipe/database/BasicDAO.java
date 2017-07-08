package org.schabi.newpipe.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Update;

import java.util.Collection;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;

@Dao
public interface BasicDAO<Entity> {
    /* Inserts */
    @Insert(onConflict = OnConflictStrategy.FAIL)
    void insert(final Entity entity);

    @Insert(onConflict = OnConflictStrategy.FAIL)
    void insertAll(final Entity... entities);

    @Insert(onConflict = OnConflictStrategy.FAIL)
    void insertAll(final Collection<Entity> entities);

    /* Searches */
    Flowable<List<Entity>> findAll();

    Flowable<List<Entity>> listByService(int serviceId);

    /* Deletes */
    @Delete
    void delete(final Entity entity);

    @Delete
    void delete(final Collection<Entity> entities);

    /* Updates */
    @Update
    void update(final Entity entity);

    @Update
    void update(final Collection<Entity> entities);
}
