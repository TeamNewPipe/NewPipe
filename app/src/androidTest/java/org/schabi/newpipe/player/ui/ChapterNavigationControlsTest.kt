/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.download.PreciseStreamSegment

class ChapterNavigationControlsTest {
    @Test fun previousAndNextSeekPreciseChaptersAndLeaveQueueHeaderControlsIntact() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.DarkTheme)
            val binding = PlayerBinding.inflate(LayoutInflater.from(context))
            val originalCount = binding.itemsListControl.childCount
            binding.itemsListPanel.visibility = View.VISIBLE
            binding.itemsListHeaderTitle.visibility = View.VISIBLE
            binding.itemsListHeaderTitle.text = "Chapter navigation title ".repeat(8)
            val navigation = ChapterNavigationControls(binding)
            val size = (360 * context.resources.displayMetrics.density).toInt()
            val directions = listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)
            fun layout(direction: Int) {
                binding.root.layoutDirection = direction
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, size, size)
            }
            fun titleBounds() = binding.itemsListHeaderTitle.run { Rect(left, top, right, bottom) }
            val originalBounds = directions.associateWith { direction ->
                layout(direction)
                titleBounds().also { assertTrue(it.width() > 0 && it.height() > 0) }
            }
            val chapters = listOf(PreciseStreamSegment("First", 0), PreciseStreamSegment("Second", 1250), PreciseStreamSegment("Third", 5000))
            val targets = mutableListOf<Long>()
            navigation.show(chapters, 0) { targets += it }
            val previous = binding.itemsListControl.children.single { it.contentDescription == context.getString(R.string.previous_chapter) }
            val next = binding.itemsListControl.children.single { it.contentDescription == context.getString(R.string.next_chapter) }
            assertFalse(previous.isEnabled)
            assertTrue(next.isEnabled)
            next.performClick()
            assertEquals(listOf(1250L), targets)
            navigation.update(chapters, 1500)
            previous.performClick()
            assertEquals(listOf(1250L, 1250L), targets)
            navigation.update(chapters, 5000)
            assertFalse(next.isEnabled)
            assertTrue(previous.isEnabled)
            for (direction in directions) {
                navigation.show(chapters, 5000) { targets += it }
                layout(direction)
                assertTrue(previous.width >= 48 * context.resources.displayMetrics.density - 1)
                assertTrue(next.width >= 48 * context.resources.displayMetrics.density - 1)
                assertTrue(previous.right <= next.left || next.right <= previous.left)
                val narrowedTitle = titleBounds()
                assertTrue(narrowedTitle.width() > 0)
                assertTrue(if (direction == View.LAYOUT_DIRECTION_RTL) narrowedTitle.left >= previous.right else narrowedTitle.right <= previous.left)
                navigation.hide()
                layout(direction)
                assertEquals(View.GONE, previous.visibility)
                assertEquals(View.GONE, next.visibility)
                // API 23 resolves relative rules to physical rules when layout params are set.
                // Verify restored geometry instead of relying on unresolved ALIGN_END values.
                assertEquals(originalBounds.getValue(direction), titleBounds())
                assertTrue(binding.itemsListHeaderTitle.width > narrowedTitle.width())
            }
            binding.shuffleButton.visibility = View.VISIBLE
            navigation.hide()
            assertEquals(View.GONE, previous.visibility)
            assertEquals(View.GONE, next.visibility)
            assertEquals(View.VISIBLE, binding.shuffleButton.visibility)
            navigation.clear()
            assertEquals(originalCount, binding.itemsListControl.childCount)
        }
    }
}
