package org.schabi.newpipe.database.stream.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public abstract class StreamStateDAO implements BasicDAO<StreamStateEntity> {
    @Override
    @Query("SELECT * FROM " + STREAM_STATE_TABLE)
    public abstract Flowable<List<StreamStateEntity>> getAll();

    @Override
    @Query("DELETE FROM " + STREAM_STATE_TABLE)
    public abstract int deleteAll();

    @Override
    public Flowable<List<StreamStateEntity>> listByService(int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("DELETE FROM " + STREAM_STATE_TABLE + " WHERE " + JOIN_STREAM_ID + " = :streamId")
    public abstract int deleteState(final long streamId);
}
