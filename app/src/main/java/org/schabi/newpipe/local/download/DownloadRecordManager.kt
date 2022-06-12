package org.schabi.newpipe.local.download

import android.content.Context
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.download.dao.DownloadDAO
import org.schabi.newpipe.database.download.entry.DownloadEntry
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DownloadRecordManager(context: Context) {

    private val database: AppDatabase
    private val downloadTable: DownloadDAO

    init {
        database = NewPipeDatabase.getInstance(context)
        downloadTable = database.downloadDAO()
    }

    fun insert(key: String, uri: String, url: String): Maybe<Long> {
        return Maybe.fromCallable {
            database.runInTransaction<Long> {
                val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
                downloadTable.insert(DownloadEntry(currentTime, key, url, uri))
            }
        }.subscribeOn(Schedulers.io())
    }

    fun getUri(key: String): Maybe<DownloadEntry> {
        return downloadTable.getUri(key).subscribeOn(Schedulers.io())
    }

    fun getUriFromUrl(url: String): Maybe<DownloadEntry> {
        return downloadTable.getUriFromUrl(url).subscribeOn(Schedulers.io())
    }

}
