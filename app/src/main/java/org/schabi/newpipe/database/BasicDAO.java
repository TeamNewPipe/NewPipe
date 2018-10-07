package org.schabi.newpipe.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Update;

import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface BasicDAO<Entity> {
    /* Inserts */
    @Insert(onConflict = OnConflictStrategy.FAIL)
    long insert(final Entity entity);

    @Insert(onConflict = OnConflictStrategy.FAIL)
    List<Long> insertAll(final Entity... entities);

    @Insert(onConflict = OnConflictStrategy.FAIL)
    List<Long> insertAll(final Collection<Entity> entities);

    /* Searches */
    Flowable<List<Entity>> getAll();

    Flowable<List<Entity>> listByService(int serviceId);

    /* Deletes */
    @Delete
    void delete(final Entity entity);

    @Delete
    int delete(final Collection<Entity> entities);

    int deleteAll();

    /* Updates */
    @Update
    int update(final Entity entity);

    @Update
    void update(final Collection<Entity> entities);
}
