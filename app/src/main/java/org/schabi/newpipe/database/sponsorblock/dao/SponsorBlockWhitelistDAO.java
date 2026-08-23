package org.schabi.newpipe.database.sponsorblock.dao;

import static org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE;
import static org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry.UPLOADER;

import androidx.room.Dao;
import androidx.room.Query;

import org.schabi.newpipe.database.BasicDAO;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

@Dao
public abstract class SponsorBlockWhitelistDAO implements BasicDAO<SponsorBlockWhitelistEntry> {
    @Override
    @Query("SELECT * FROM " + SPONSORBLOCK_WHITELIST_TABLE)
    public abstract Flowable<List<SponsorBlockWhitelistEntry>> getAll();

    @Override
    @Query("DELETE FROM " + SPONSORBLOCK_WHITELIST_TABLE)
    public abstract int deleteAll();

    @Override
    public Flowable<List<SponsorBlockWhitelistEntry>> listByService(final int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("DELETE FROM " + SPONSORBLOCK_WHITELIST_TABLE + " WHERE " + UPLOADER + " = :uploader")
    public abstract int deleteByUploader(String uploader);

    @Query("SELECT 1 FROM " + SPONSORBLOCK_WHITELIST_TABLE + " WHERE " + UPLOADER + " = :uploader")
    public abstract boolean exists(String uploader);
}
