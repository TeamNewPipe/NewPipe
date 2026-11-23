/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
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
            val titleLayout = binding.itemsListHeaderTitle.layoutParams as RelativeLayout.LayoutParams
            val originalEnd = titleLayout.getRule(RelativeLayout.ALIGN_END)
            val navigation = ChapterNavigationControls(binding)
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
            val size = (360 * context.resources.displayMetrics.density).toInt()
            binding.itemsListPanel.visibility = View.VISIBLE
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                binding.root.layoutDirection = direction
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, size, size)
                assertTrue(previous.width >= 48 * context.resources.displayMetrics.density - 1)
                assertTrue(next.width >= 48 * context.resources.displayMetrics.density - 1)
                assertTrue(previous.right <= next.left || next.right <= previous.left)
            }
            binding.shuffleButton.visibility = View.VISIBLE
            navigation.hide()
            assertEquals(View.GONE, previous.visibility)
            assertEquals(View.GONE, next.visibility)
            assertEquals(View.VISIBLE, binding.shuffleButton.visibility)
            assertEquals(originalEnd, (binding.itemsListHeaderTitle.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.ALIGN_END))
            navigation.clear()
            assertEquals(originalCount, binding.itemsListControl.childCount)
        }
    }
}
