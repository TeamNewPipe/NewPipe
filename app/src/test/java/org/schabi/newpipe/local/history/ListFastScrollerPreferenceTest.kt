package org.schabi.newpipe.local.history

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], qualifiers = "mdpi")
class ListFastScrollerPreferenceTest {
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var host: FrameLayout
    private lateinit var list: RecyclerView
    private lateinit var track: HistoryDateFastScroller
    private lateinit var scroller: ListFastScroller
    private lateinit var key: String

    @Before
    fun setup() {
        activityController = Robolectric.buildActivity(Activity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.LightTheme)
        activityController.setup().visible()
        key = activity.getString(R.string.show_list_scrollbar_key)
        PreferenceManager.getDefaultSharedPreferences(activity).edit().remove(key).commit()
        host = FrameLayout(activity)
        list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            isVerticalScrollBarEnabled = true
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 80
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = object : RecyclerView.ViewHolder(
                    TextView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48)
                    }
                ) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    (holder.itemView as TextView).text = "Item $position"
                }
            }
        }
        host.addView(list, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(host)
        scroller = requireNotNull(ListFastScroller.attach(list))
        track = host.getChildAt(1) as HistoryDateFastScroller
        layout()
    }

    @After
    fun teardown() {
        scroller.close()
        PreferenceManager.getDefaultSharedPreferences(activity).edit().remove(key).commit()
        activityController.pause().stop().destroy()
    }

    @Test
    fun disablingTheOpenListHidesBothScrollbarsAndKeepsOrdinaryScrolling() {
        assertEquals(View.VISIBLE, track.visibility)
        assertFalse(list.isVerticalScrollBarEnabled)
        setEnabled(false)
        assertEquals(View.GONE, track.visibility)
        assertFalse(list.isVerticalScrollBarEnabled)
        val layoutManager = list.layoutManager as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(50, 0)
        layout()
        assertEquals(50, layoutManager.findFirstVisibleItemPosition())
        setEnabled(true)
        assertEquals(View.VISIBLE, track.visibility)
        assertFalse(list.isVerticalScrollBarEnabled)
        assertEquals(50, layoutManager.findFirstVisibleItemPosition())
    }

    @Test
    fun disablingDuringADragDismissesTheBubbleAndEndsTheGesture() {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, track.width - 8f, 18f, 0)
        try {
            assertTrue(track.onTouchEvent(down))
            assertTrue(track.isDragging)
            setEnabled(false)
            assertFalse(track.isDragging)
            assertEquals(View.GONE, track.visibility)
        } finally {
            down.recycle()
        }
    }

    @Test
    fun closedControllerRestoresOriginalStateAndStopsPreferenceCallbacks() {
        scroller.close()
        assertEquals(1, host.childCount)
        assertTrue(list.isVerticalScrollBarEnabled)
        setEnabled(false)
        assertEquals(1, host.childCount)
        assertTrue(list.isVerticalScrollBarEnabled)
    }

    private fun setEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean(key, enabled).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun layout() {
        shadowOf(Looper.getMainLooper()).idle()
        host.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 320, 480)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
