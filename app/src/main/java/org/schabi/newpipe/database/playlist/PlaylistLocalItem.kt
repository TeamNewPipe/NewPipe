package org.schabi.newpipe.database.playlist

import org.schabi.newpipe.database.LocalItem

open interface PlaylistLocalItem : LocalItem {
    fun getOrderingName(): String
    fun getDisplayIndex(): Long
    fun getUid(): Long
    fun setDisplayIndex(displayIndex: Long)
}
