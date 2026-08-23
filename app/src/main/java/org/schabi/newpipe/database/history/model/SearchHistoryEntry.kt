package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

@Entity(
    tableName = SearchHistoryEntry.TABLE_NAME,
    indices = [Index(value = [SearchHistoryEntry.SEARCH])]
)
data class SearchHistoryEntry(
    @field:ColumnInfo(name = CREATION_DATE) var creationDate: OffsetDateTime?,
    @field:ColumnInfo(
        name = SERVICE_ID
    ) var serviceId: Int,
    @field:ColumnInfo(name = SEARCH) var search: String?
) {
    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    fun hasEqualValues(otherEntry: SearchHistoryEntry): Boolean {
        return (
            serviceId == otherEntry.serviceId &&
                search == otherEntry.search
            )
    }

    companion object {
        const val ID = "id"
        const val TABLE_NAME = "search_history"
        const val SERVICE_ID = "service_id"
        const val CREATION_DATE = "creation_date"
        const val SEARCH = "search"
    }
}
