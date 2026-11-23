/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.schabi.newpipe.profiles.ProfileManager

@Entity(
    tableName = HistorySyncChangeEntity.TABLE_NAME,
    primaryKeys = [
        HistorySyncChangeEntity.CATEGORY,
        HistorySyncChangeEntity.ORIGIN_PEER_ID,
        HistorySyncChangeEntity.ORIGIN_REVISION
    ],
    indices = [
        Index(
            value = [
                HistorySyncChangeEntity.CATEGORY,
                HistorySyncChangeEntity.RECORD_ID
            ]
        ),
        Index(
            value = [
                HistorySyncChangeEntity.CATEGORY,
                HistorySyncChangeEntity.LAMPORT_VERSION,
                HistorySyncChangeEntity.ORIGIN_PEER_ID,
                HistorySyncChangeEntity.ORIGIN_REVISION
            ]
        )
    ]
)
data class HistorySyncChangeEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(
        name = PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    val profileId: String = ProfileManager.DEFAULT_PROFILE_ID,

    @ColumnInfo(name = RECORD_TYPE)
    val recordType: String,

    @ColumnInfo(name = CHANGE_TYPE)
    val changeType: String,

    @ColumnInfo(name = RECORD_JSON)
    val recordJson: String?
) {
    companion object {
        const val TABLE_NAME = "history_sync_changes"
        const val CATEGORY = "category"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val LAMPORT_VERSION = "lamport_version"
        const val RECORD_ID = "record_id"
        const val PROFILE_ID = "profile_id"
        const val RECORD_TYPE = "record_type"
        const val CHANGE_TYPE = "change_type"
        const val RECORD_JSON = "record_json"
    }
}

@Entity(
    tableName = HistorySyncRecordEntity.TABLE_NAME,
    primaryKeys = [
        HistorySyncRecordEntity.CATEGORY,
        HistorySyncRecordEntity.RECORD_ID
    ],
    indices = [
        Index(
            value = [
                HistorySyncRecordEntity.CATEGORY,
                HistorySyncRecordEntity.RECORD_TYPE
            ]
        ),
        Index(
            value = [
                HistorySyncRecordEntity.CATEGORY,
                HistorySyncRecordEntity.PROFILE_ID,
                HistorySyncRecordEntity.STREAM_KEY
            ]
        )
    ]
)
data class HistorySyncRecordEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = RECORD_ID)
    val recordId: String,

    @ColumnInfo(
        name = PROFILE_ID,
        defaultValue = "'00000000-0000-0000-0000-000000000000'"
    )
    val profileId: String = ProfileManager.DEFAULT_PROFILE_ID,

    @ColumnInfo(name = RECORD_TYPE)
    val recordType: String,

    @ColumnInfo(name = LAMPORT_VERSION)
    val lamportVersion: Long,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ORIGIN_REVISION)
    val originRevision: Long,

    @ColumnInfo(name = IS_DELETED)
    val isDeleted: Boolean,

    @ColumnInfo(name = RECORD_JSON)
    val recordJson: String?,

    @ColumnInfo(name = STREAM_KEY)
    val streamKey: String? = null
) {
    companion object {
        const val TABLE_NAME = "history_sync_records"
        const val CATEGORY = "category"
        const val RECORD_ID = "record_id"
        const val PROFILE_ID = "profile_id"
        const val RECORD_TYPE = "record_type"
        const val LAMPORT_VERSION = "lamport_version"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ORIGIN_REVISION = "origin_revision"
        const val IS_DELETED = "is_deleted"
        const val RECORD_JSON = "record_json"
        const val STREAM_KEY = "stream_key"
    }
}

@Entity(
    tableName = HistorySyncOriginStateEntity.TABLE_NAME,
    primaryKeys = [
        HistorySyncOriginStateEntity.CATEGORY,
        HistorySyncOriginStateEntity.ORIGIN_PEER_ID
    ]
)
data class HistorySyncOriginStateEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = CONTIGUOUS_REVISION)
    val contiguousRevision: Long
) {
    companion object {
        const val TABLE_NAME = "history_sync_origin_state"
        const val CATEGORY = "category"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val CONTIGUOUS_REVISION = "contiguous_revision"
    }
}

@Entity(
    tableName = HistorySyncPeerStateEntity.TABLE_NAME,
    primaryKeys = [
        HistorySyncPeerStateEntity.CATEGORY,
        HistorySyncPeerStateEntity.PEER_ID,
        HistorySyncPeerStateEntity.ORIGIN_PEER_ID
    ],
    indices = [
        Index(
            value = [
                HistorySyncPeerStateEntity.CATEGORY,
                HistorySyncPeerStateEntity.ORIGIN_PEER_ID
            ]
        )
    ]
)
data class HistorySyncPeerStateEntity(
    @ColumnInfo(name = CATEGORY)
    val category: String,

    @ColumnInfo(name = PEER_ID)
    val peerId: String,

    @ColumnInfo(name = ORIGIN_PEER_ID)
    val originPeerId: String,

    @ColumnInfo(name = ACKNOWLEDGED_REVISION)
    val acknowledgedRevision: Long
) {
    companion object {
        const val TABLE_NAME = "history_sync_peer_state"
        const val CATEGORY = "category"
        const val PEER_ID = "peer_id"
        const val ORIGIN_PEER_ID = "origin_peer_id"
        const val ACKNOWLEDGED_REVISION = "acknowledged_revision"
    }
}
