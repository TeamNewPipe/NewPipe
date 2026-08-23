package org.schabi.newpipe.database.sponsorblock.dao;

import static org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry.SPONSORBLOCK_WHITELIST_TABLE;
import static org.schabi.newpipe.database.sponsorblock.dao.SponsorBlockWhitelistEntry.UPLOADER;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = SPONSORBLOCK_WHITELIST_TABLE,
        primaryKeys = {UPLOADER}
)
public class SponsorBlockWhitelistEntry {
    public static final String SPONSORBLOCK_WHITELIST_TABLE = "sponsorblock_whitelist";
    public static final String UPLOADER = "uploader";

    @NonNull
    @ColumnInfo(name = UPLOADER)
    private String uploader;

    public SponsorBlockWhitelistEntry(final @NonNull String uploader) {
        this.uploader = uploader;
    }

    @NonNull
    public String getUploader() {
        return uploader;
    }

    public void setUploader(final @NonNull String uploader) {
        this.uploader = uploader;
    }
}
