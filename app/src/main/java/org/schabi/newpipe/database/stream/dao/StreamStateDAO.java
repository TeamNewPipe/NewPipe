package org.schabi.newpipe.database.stream.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public interface StreamStateDAO extends BasicDAO<StreamStateEntity> {
    @Override
    @Query("SELECT * FROM " + STREAM_STATE_TABLE)
    Flowable<List<StreamStateEntity>> getAll();

    @Override
    @Query("DELETE FROM " + STREAM_STATE_TABLE)
    int deleteAll();

    @Override
    default Flowable<List<StreamStateEntity>> listByService(final int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("SELECT * FROM " + STREAM_STATE_TABLE + " WHERE " + JOIN_STREAM_ID + " = :streamId")
    Flowable<List<StreamStateEntity>> getState(long streamId);

    @Query("DELETE FROM " + STREAM_STATE_TABLE + " WHERE " + JOIN_STREAM_ID + " = :streamId")
    int deleteState(long streamId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void silentInsertInternal(StreamStateEntity streamState);

    @Transaction
    default long upsert(final StreamStateEntity stream) {
        silentInsertInternal(stream);
        return update(stream);
    }
}
