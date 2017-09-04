package org.schabi.newpipe.database.stream;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.BasicDAO;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.stream.StreamEntity.STREAM_SERVICE_ID;
import static org.schabi.newpipe.database.stream.StreamEntity.STREAM_TABLE;

@Dao
public interface StreamDAO extends BasicDAO<StreamEntity> {
    @Override
    @Query("SELECT * FROM " + STREAM_TABLE)
    Flowable<List<StreamEntity>> getAll();

    @Override
    @Query("SELECT * FROM " + STREAM_TABLE + " WHERE " + STREAM_SERVICE_ID + " = :serviceId")
    Flowable<List<StreamEntity>> listByService(int serviceId);
}
