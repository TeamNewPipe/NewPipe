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

/**
 * Layout tests for [R.layout.fragment_history].
 *
 * They guard the regression described in issue #13487: the history controls must be laid out
 * above the list and must not be part of the scrolling adapter, otherwise they scroll out of the
 * screen again as soon as a history list grows.
 */
@RunWith(AndroidJUnit4::class)
class HistoryControlsLayoutTest {
    @Test
    fun controlsStayAboveLongListInPortrait() = onMainThread {
        verifyControlsStayPinned(portraitWidth, portraitHeight)
    }

    @Test
    fun controlsStayAboveLongListInLandscape() = onMainThread {
        verifyControlsStayPinned(portraitHeight, portraitWidth)
    }

    @Test
    fun controlsDoNotCoverTheList() = onMainThread {
        val history = layoutHistory(portraitWidth, portraitHeight)
        assertTrue(history.controls.bottom <= history.container.top)
    }

    @Test
    fun controlsLeaveMostOfTheScreenToTheListInLandscape() = onMainThread {
        val history = layoutHistory(portraitHeight, portraitWidth)
        // The two 50dp rows must not eat up a meaningful part of a landscape screen.
        assertTrue(history.controls.bottom < portraitWidth * 0.35f)
        assertTrue(history.list.height > portraitWidth / 2)
    }

    @Test
    fun controlsStartHiddenUntilHistoryIsLoaded() = onMainThread {
        val root = inflateHistory()
        assertEquals(View.GONE, root.findViewById<View>(R.id.history_controls).visibility)
    }

    private fun verifyControlsStayPinned(width: Int, height: Int) {
        val history = layoutHistory(width, height)
        val controls = history.controls
        val list = history.list
        val manager = history.manager

        assertTrue(controls.height > 0)
        assertTrue(list.height > 0)
        assertTrue(list.canScrollVertically(1))
        // The controls are a sibling of the list container, not a header inside the adapter.
        assertFalse(isDescendantOf(controls, list))

        val pinnedBottom = controls.bottom
        val containerTop = history.container.top
        assertEquals(pinnedBottom, containerTop)

        list.scrollBy(0, height * 4)
        assertTrue(manager.findFirstVisibleItemPosition() > 0)
        assertEquals(containerTop, history.container.top)
        assertTrue(list.canScrollVertically(-1))

        list.scrollBy(0, -height * 4)
        assertEquals(0, manager.findFirstVisibleItemPosition())
        assertEquals(containerTop, history.container.top)
    }

    /**
     * Inflates and lays out [R.layout.fragment_history], then pretends that the history was loaded
     * so that the controls are visible. Must be called on the main thread.
     */
    private fun layoutHistory(width: Int, height: Int): LaidOutHistory {
        val root = inflateHistory()
        val controls = root.findViewById<View>(R.id.history_controls)
        controls.visibility = View.VISIBLE
        controls.findViewById<View>(R.id.playlist_control).visibility = View.VISIBLE

        val container = root.findViewById<View>(R.id.history_list_container)
        val list = root.findViewById<RecyclerView>(R.id.items_list)
        val manager = LinearLayoutManager(root.context)
        list.layoutManager = manager
        list.adapter = HistoryRows()

        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)

        return LaidOutHistory(controls, container, list, manager)
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

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    /**
     * Screen sizes in dp converted to pixels, so that the assertions do not depend on the density
     * of the emulator the tests happen to run on.
     */
    private val density: Float
        get() = ApplicationProvider.getApplicationContext<Context>().resources
            .displayMetrics.density

    private val portraitWidth: Int
        get() = (360 * density).toInt()

    private val portraitHeight: Int
        get() = (640 * density).toInt()

    private class LaidOutHistory(
        val controls: View,
        val container: View,
        val list: RecyclerView,
        val manager: LinearLayoutManager
    )

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
