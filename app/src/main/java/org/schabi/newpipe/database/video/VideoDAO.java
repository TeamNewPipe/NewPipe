package org.schabi.newpipe.database.video;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.BasicDAO;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface VideoDAO extends BasicDAO<VideoEntity> {
    @Override
    @Query("SELECT * FROM videos")
    Flowable<List<VideoEntity>> findAll();

    @Override
    @Query("SELECT * FROM videos WHERE service_id = :serviceId")
    Flowable<List<VideoEntity>> listByService(int serviceId);
}
