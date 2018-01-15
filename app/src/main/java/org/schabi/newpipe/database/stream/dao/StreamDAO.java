package org.schabi.newpipe.database.stream.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_SERVICE_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_URL;

@Dao
public abstract class StreamDAO implements BasicDAO<StreamEntity> {
    @Override
    @Query("SELECT * FROM " + STREAM_TABLE)
    public abstract Flowable<List<StreamEntity>> getAll();

    @Override
    @Query("DELETE FROM " + STREAM_TABLE)
    public abstract int deleteAll();

    @Override
    @Query("SELECT * FROM " + STREAM_TABLE + " WHERE " + STREAM_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<StreamEntity>> listByService(int serviceId);

    @Query("SELECT * FROM " + STREAM_TABLE + " WHERE " +
            STREAM_URL + " LIKE :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<StreamEntity>> getStream(long serviceId, String url);

    @Query("SELECT * FROM " + STREAM_TABLE + " WHERE " +
            STREAM_URL + " LIKE :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    abstract List<StreamEntity> getStreamInternal(long serviceId, String url);

    @Transaction
    public long upsert(StreamEntity stream) {
        final List<StreamEntity> streams = getStreamInternal(stream.getServiceId(), stream.getUrl());

        final long uid;
        if (streams.isEmpty()) {
            uid = insert(stream);
        } else {
            uid = streams.get(0).getUid();
            stream.setUid(uid);
            update(stream);
        }
        return uid;
    }
}
