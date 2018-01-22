package org.schabi.newpipe.database.stream.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
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
            STREAM_URL + " = :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<StreamEntity>> getStream(long serviceId, String url);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void silentInsertAllInternal(final List<StreamEntity> streams);

    @Query("SELECT " + STREAM_ID + " FROM " + STREAM_TABLE + " WHERE " +
            STREAM_URL + " = :url AND " +
            STREAM_SERVICE_ID + " = :serviceId")
    abstract Long getStreamIdInternal(long serviceId, String url);

    @Transaction
    public long upsert(StreamEntity stream) {
        final Long streamIdCandidate = getStreamIdInternal(stream.getServiceId(), stream.getUrl());

        if (streamIdCandidate == null) {
            return insert(stream);
        } else {
            stream.setUid(streamIdCandidate);
            update(stream);
            return streamIdCandidate;
        }
    }

    @Transaction
    public List<Long> upsertAll(List<StreamEntity> streams) {
        silentInsertAllInternal(streams);

        final List<Long> streamIds = new ArrayList<>(streams.size());
        for (StreamEntity stream : streams) {
            final Long streamId = getStreamIdInternal(stream.getServiceId(), stream.getUrl());
            if (streamId == null) {
                throw new IllegalStateException("StreamID cannot be null just after insertion.");
            }

            streamIds.add(streamId);
            stream.setUid(streamId);
        }

        update(streams);
        return streamIds;
    }
}
