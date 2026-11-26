package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.FEED_GROUP_TABLE
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.PROFILE_ID
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.SORT_ORDER
import org.schabi.newpipe.local.subscription.FeedGroupIcon

@Entity(
    tableName = FEED_GROUP_TABLE,
    indices = [Index(value = [PROFILE_ID, SORT_ORDER])]
)
data class FeedGroupEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    val uid: Long,

    @ColumnInfo(name = NAME)
    var name: String,

    @ColumnInfo(name = ICON)
    var icon: FeedGroupIcon,

    @ColumnInfo(name = SORT_ORDER)
    var sortOrder: Long = -1,

    @ColumnInfo(
        name = PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    var profileId: String = DEFAULT_PROFILE_ID
) {
    companion object {
        const val FEED_GROUP_TABLE = "feed_group"

        const val ID = "uid"
        const val NAME = "name"
        const val ICON = "icon_id"
        const val SORT_ORDER = "sort_order"
        const val PROFILE_ID = "profile_id"
        const val DEFAULT_PROFILE_ID = "00000000-0000-0000-0000-000000000000"

        const val GROUP_ALL_ID = -1L

        // -2 is reserved for notification-enabled subscriptions in FeedLoadManager.
        const val GROUP_UNGROUPED_ID = -3L

        fun isVirtualGroup(groupId: Long): Boolean = groupId == GROUP_ALL_ID || groupId == GROUP_UNGROUPED_ID
    }
}
