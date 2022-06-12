package org.schabi.newpipe.database.download.entry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

@Entity(tableName = DownloadEntry.TABLE_NAME)
data class DownloadEntry(
    @field:ColumnInfo(name = CREATION_DATE) var creationDate: OffsetDateTime?,
    @field:ColumnInfo(name = ID_KEY) var idKey: String,
    @field:ColumnInfo(name = URL_KEY) var url: String,
    @field:ColumnInfo(name = URI_VALUE) var uriValue: String
) {
    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    companion object {
        const val ID = "id"
        const val TABLE_NAME = "download"
        const val ID_KEY = "id_key"
        const val URL_KEY = "url_key"
        const val URI_VALUE = "uri_value"
        const val CREATION_DATE = "creation_date"
    }
}
