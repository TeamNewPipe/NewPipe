/*
 * SPDX-FileCopyrightText: 2025 NewPipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = PlaylistFolderEntity.FOLDER_TABLE)
data class PlaylistFolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = FOLDER_ID)
    var uid: Long = 0,

    @ColumnInfo(name = FOLDER_NAME)
    var name: String,

    @ColumnInfo(name = FOLDER_SORT_ORDER)
    var sortOrder: Long = 0
){
    companion object {
        const val FOLDER_TABLE = "playlist_folders"
        const val FOLDER_ID = "uid"
        const val FOLDER_NAME = "name"
        const val FOLDER_SORT_ORDER = "sort_order"
    }
}
