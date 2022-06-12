package org.schabi.newpipe.database.download.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.download.entry.DownloadEntry

@Dao
interface DownloadDAO {
    @Query(
        "SELECT * FROM " + DownloadEntry.TABLE_NAME +
            " WHERE " + DownloadEntry.ID_KEY + " = :key"
    )
    fun getUri(key: String): Maybe<DownloadEntry>

    @Query(
        "SELECT * FROM " + DownloadEntry.TABLE_NAME +
            " WHERE " + DownloadEntry.URL_KEY + " = :url"
    )
    fun getUriFromUrl(url: String): Maybe<DownloadEntry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: DownloadEntry?): Long
}
