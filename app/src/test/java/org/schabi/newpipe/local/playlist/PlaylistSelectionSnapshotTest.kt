/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class PlaylistSelectionSnapshotTest {
    @Test
    fun selectingOneDuplicateOccurrenceLeavesTheOtherCopyInItsOriginalPosition() {
        val firstCopy = entry(7, 0)
        val middle = entry(8, 1)
        val secondCopy = firstCopy.copy(joinIndex = 2)
        val last = entry(9, 3)
        val saved = listOf(firstCopy, middle, secondCopy, last)
        val selection = PlaylistSelectionSnapshot("profile", 42, saved, saved)

        assertTrue(selection.toggle(secondCopy))
        assertEquals(1, selection.selectedCount)
        assertFalse(selection.isSelected(firstCopy))
        assertTrue(selection.isSelected(secondCopy))
        assertSame(secondCopy, selection.selectedInDisplayOrder().single())
        assertEquals(setOf(2), selection.selectedSavedIndices())
        assertEquals(listOf(firstCopy, middle, last), selection.remainingSavedOrder())
        assertSame(firstCopy, selection.remainingSavedOrder().first())
    }

    @Test
    fun selectAllUsesOnlyTheFilteredRowsAndKeepsTheirDisplayedOrderForBatchActions() {
        val saved = (0..4).map { entry(it.toLong(), it) }
        val displayed = listOf(saved[3], saved[1])
        val selection = PlaylistSelectionSnapshot("profile", 42, saved, displayed)

        assertFalse("A hidden row cannot be selected through an old click", selection.toggle(saved[2]))
        selection.toggleAllDisplayed()
        assertEquals(2, selection.selectedCount)
        assertEquals(displayed, selection.selectedInDisplayOrder())
        assertEquals(setOf(1, 3), selection.selectedSavedIndices())
        assertEquals(listOf(saved[0], saved[2], saved[4]), selection.remainingSavedOrder())
        selection.toggleAllDisplayed()
        assertEquals(0, selection.selectedCount)
        assertEquals(saved, selection.remainingSavedOrder())
    }

    @Test
    fun selectionOrderDoesNotDependOnTheOrderOfTaps() {
        val saved = (0..2).map { entry(it.toLong(), it) }
        val displayed = saved.reversed()
        val selection = PlaylistSelectionSnapshot("profile", 42, saved, displayed)

        selection.toggle(saved[0])
        selection.toggle(saved[2])
        assertEquals(listOf(saved[2], saved[0]), selection.selectedInDisplayOrder())
        assertEquals(setOf(0, 2), selection.selectedSavedIndices())
        selection.toggle(saved[0])
        assertEquals(listOf(saved[2]), selection.selectedInDisplayOrder())
    }

    @Test
    fun aReloadOrScopeChangeCannotReuseAnOldSelection() {
        val saved = (0..2).map { entry(it.toLong(), it) }
        val displayed = listOf(saved[2], saved[0])
        val selection = PlaylistSelectionSnapshot("profile", 42, saved, displayed)
        selection.toggle(saved[0])

        assertTrue(selection.isCurrent("profile", 42, saved.toList(), displayed.toList()))
        assertFalse(selection.isCurrent("other-profile", 42, saved, displayed))
        assertFalse(selection.isCurrent("profile", 43, saved, displayed))
        assertFalse(selection.isCurrent("profile", 42, saved.reversed(), displayed))
        assertFalse(selection.isCurrent("profile", 42, saved, displayed.reversed()))
        assertFalse(selection.isCurrent("profile", 42, saved, saved))
        val reloaded = saved.map { it.copy() }
        assertFalse(selection.isCurrent("profile", 42, reloaded, listOf(reloaded[2], reloaded[0])))
        assertFalse("Equal values from a fresh row must not toggle a stale occurrence", selection.toggle(saved[0].copy()))
        assertEquals(1, selection.selectedCount)
    }

    @Test
    fun changingTheInputListsCannotChangeTheCapturedRemovalPlan() {
        val first = entry(1, 0)
        val second = entry(2, 1)
        val saved = mutableListOf(first, second)
        val displayed = mutableListOf(second)
        val selection = PlaylistSelectionSnapshot("profile", 42, saved, displayed)
        saved.clear()
        displayed.clear()

        assertTrue(selection.toggle(second))
        assertEquals(listOf(second), selection.selectedInDisplayOrder())
        assertEquals(listOf(first), selection.remainingSavedOrder())
        assertFalse(selection.isCurrent("profile", 42, saved, displayed))
    }

    private fun entry(id: Long, joinIndex: Int) = PlaylistStreamEntry(
        streamEntity = StreamEntity(
            uid = id,
            serviceId = 0,
            url = "https://example.test/video/$id",
            title = "Video $id",
            streamType = StreamType.VIDEO_STREAM,
            duration = 60,
            uploader = "Example channel"
        ),
        progressMillis = 0,
        streamId = id,
        joinIndex = joinIndex
    )
}
