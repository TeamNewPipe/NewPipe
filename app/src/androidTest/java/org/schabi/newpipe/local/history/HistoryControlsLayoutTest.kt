package org.schabi.newpipe.local.history

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class HistoryControlsLayoutTest {
    @Test
    fun controlsStayAboveLongListInPortrait() = verifyScrollingLayout(480, 800)

    @Test
    fun controlsStayAboveLongListInLandscape() = verifyScrollingLayout(800, 480)

    @Test
    fun controlsStartHiddenUntilHistoryIsLoaded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = inflateHistory()
            assertEquals(View.GONE, root.findViewById<View>(R.id.history_controls).visibility)
        }
    }

    private fun verifyScrollingLayout(width: Int, height: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = inflateHistory()
            val controls = root.findViewById<View>(R.id.history_controls)
            controls.visibility = View.VISIBLE
            controls.findViewById<View>(R.id.playlist_control).visibility = View.VISIBLE
            val list = root.findViewById<RecyclerView>(R.id.items_list)
            val manager = LinearLayoutManager(root.context)
            list.layoutManager = manager
            list.adapter = HistoryRows()

            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
            val originalTop = controls.top
            val originalBottom = controls.bottom
            assertTrue(originalBottom > originalTop)
            assertTrue(list.height > 0)
            assertTrue(list.canScrollVertically(1))
            assertFalse(isDescendantOf(controls, list))
            // The included list container starts below the controls, so no row is covered.
            assertTrue((list.parent as View).top >= originalBottom)

            list.scrollBy(0, height * 4)
            assertTrue(manager.findFirstVisibleItemPosition() > 0)
            assertEquals(originalTop, controls.top)
            assertEquals(originalBottom, controls.bottom)

            list.scrollBy(0, -height * 4)
            assertEquals(0, manager.findFirstVisibleItemPosition())
            assertEquals(originalTop, controls.top)
        }
    }

    private fun inflateHistory(): View {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.LightTheme
        )
        return LayoutInflater.from(context).inflate(R.layout.fragment_history, null, false)
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private class HistoryRows : RecyclerView.Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val row = TextView(parent.context)
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
            return RowHolder(row)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            (holder.itemView as TextView).text = "History entry $position"
        }

        override fun getItemCount() = 100
    }

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view)
}
