package org.schabi.newpipe.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

import org.schabi.newpipe.database.channel.ChannelDAO;
import org.schabi.newpipe.database.channel.ChannelEntity;
import org.schabi.newpipe.database.video.VideoDAO;
import org.schabi.newpipe.database.video.VideoEntity;

@Database(entities = {ChannelEntity.class, VideoEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase{

    public static final String DATABASE_NAME = "basic.db";

    public abstract ChannelDAO channelDAO();
    public abstract VideoDAO videoDAO();
}
