package org.schabi.newpipe.database.channel;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.BasicDAO;

import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.channel.ChannelEntity.CHANNEL_SERVICE_ID;
import static org.schabi.newpipe.database.channel.ChannelEntity.CHANNEL_TABLE;
import static org.schabi.newpipe.database.channel.ChannelEntity.CHANNEL_URL;

@Dao
public interface ChannelDAO extends BasicDAO<ChannelEntity> {
    @Override
    @Query("SELECT * FROM " + CHANNEL_TABLE)
    Flowable<List<ChannelEntity>> findAll();

    @Override
    @Query("SELECT * FROM " + CHANNEL_TABLE + " WHERE " + CHANNEL_SERVICE_ID + " = :serviceId")
    Flowable<List<ChannelEntity>> listByService(int serviceId);

    /* Single entity query should not use flowable in case of empty result */
    @Query("SELECT * FROM " + CHANNEL_TABLE + " WHERE " + CHANNEL_URL + " LIKE :url LIMIT 1")
    ChannelEntity findByUrl(String url);
}
