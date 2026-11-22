/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

/** Selection belongs to playlist occurrences and the exact saved/displayed snapshot. */
class PlaylistSelectionSnapshot(
    private val profileId: String,
    private val playlistId: Long,
    savedOrder: List<PlaylistStreamEntry>,
    displayed: List<PlaylistStreamEntry>
) {
    private data class Occurrence(val streamId: Long, val joinIndex: Int)

    private val savedOrder = savedOrder.toList()
    private val displayed = displayed.toList()
    private val savedKeys = this.savedOrder.map(::key)
    private val displayedKeys = this.displayed.map(::key)
    private val displayedByKey = this.displayed.associateBy(::key)
    private val selected = mutableSetOf<Occurrence>()

    init {
        require(savedKeys.distinct().size == savedKeys.size) { "Playlist occurrences must be unique" }
        require(displayedKeys.distinct().size == displayedKeys.size) { "Displayed occurrences must be unique" }
        val savedByKey = this.savedOrder.associateBy(::key)
        require(this.displayed.all { savedByKey[key(it)] === it }) { "Displayed rows must belong to the saved snapshot" }
    }

    val selectedCount: Int
        get() = selected.size

    /** Returns false for a recycled or hidden row outside this selection's displayed scope. */
    fun toggle(entry: PlaylistStreamEntry): Boolean {
        val occurrence = key(entry)
        if (displayedByKey[occurrence] !== entry) return false
        if (!selected.add(occurrence)) selected.remove(occurrence)
        return true
    }

    fun toggleAllDisplayed() {
        if (selected.size == displayed.size) selected.clear() else selected.addAll(displayedKeys)
    }

    fun isSelected(entry: PlaylistStreamEntry): Boolean = displayedByKey[key(entry)] === entry && key(entry) in selected

    fun selectedInDisplayOrder(): List<PlaylistStreamEntry> = displayed.filter { key(it) in selected }

    fun selectedSavedIndices(): Set<Int> = savedKeys.indices.filterTo(linkedSetOf()) { savedKeys[it] in selected }

    fun remainingSavedOrder(): List<PlaylistStreamEntry> = savedOrder.filter { key(it) !in selected }

    fun isCurrent(profileId: String, playlistId: Long, savedOrder: List<PlaylistStreamEntry>, displayed: List<PlaylistStreamEntry>): Boolean = this.profileId == profileId && this.playlistId == playlistId &&
        sameRows(this.savedOrder, savedKeys, savedOrder) && sameRows(this.displayed, displayedKeys, displayed)

    private fun sameRows(expected: List<PlaylistStreamEntry>, keys: List<Occurrence>, actual: List<PlaylistStreamEntry>): Boolean = expected.size == actual.size && expected.indices.all { expected[it] === actual[it] && keys[it] == key(actual[it]) }

    private fun key(entry: PlaylistStreamEntry) = Occurrence(entry.streamId, entry.joinIndex)
}
