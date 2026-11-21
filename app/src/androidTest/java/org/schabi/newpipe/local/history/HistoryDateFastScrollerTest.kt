package org.schabi.newpipe.local.history

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDateFastScrollerTest {
    @Test
    fun trackStaysNearThePhysicalScreenEdgeInLtrAndRtl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val width = (48 * density).roundToInt()
            val view = HistoryDateFastScroller(instrumentation.targetContext)
            view.layout(0, 0, width, (400 * density).roundToInt())

            assertEquals(
                width - 8 * density,
                view.getTrackCenterXForLayoutDirection(View.LAYOUT_DIRECTION_LTR),
                0.5f
            )
            assertEquals(
                8 * density,
                view.getTrackCenterXForLayoutDirection(View.LAYOUT_DIRECTION_RTL),
                0.5f
            )
        }
    }

    @Test
    fun dragMapsTrackEndpointsToHistoryEndpoints() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = HistoryDateFastScroller(instrumentation.targetContext)
            var selectedPosition = -1
            val dragStates = mutableListOf<Boolean>()
            view.setOnPositionChangedListener { selectedPosition = it }
            view.setOnDragStateChangedListener { dragStates += it }
            view.setLabelProvider { position -> "Date $position" }
            view.setItemCount(100)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 48, 1000)

            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 18f))
            assertTrue(view.isDragging)
            view.onTouchEvent(event(MotionEvent.ACTION_MOVE, 982f))

            assertEquals(99, selectedPosition)
            assertTrue(view.contentDescription.toString().contains("Date 99"))

            view.onTouchEvent(event(MotionEvent.ACTION_UP, 982f))
            assertFalse(view.isDragging)
            assertEquals(listOf(true, false), dragStates)
        }
    }

    @Test
    fun subscriptionFastScrollerLeavesCollapsedHeaderButtonUncovered() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val container = RelativeLayout(context)
            val list = RecyclerView(context).apply {
                id = View.generateViewId()
                layoutManager = LinearLayoutManager(context)
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int
                    ): RecyclerView.ViewHolder {
                        return object : RecyclerView.ViewHolder(View(parent.context)) {}
                    }

                    override fun onBindViewHolder(
                        holder: RecyclerView.ViewHolder,
                        position: Int
                    ) = Unit

                    override fun getItemCount(): Int = 75
                }
            }
            container.addView(
                list,
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val scroller = requireNotNull(ListFastScroller.attach(list, topClearanceDp = 56))
            val overlay = container.getChildAt(1) as HistoryDateFastScroller
            val params = overlay.layoutParams as RelativeLayout.LayoutParams
            val expected = (56 * context.resources.displayMetrics.density + 0.5f).toInt()
            assertEquals(expected, params.topMargin)
            assertTrue(overlay.visibility == View.VISIBLE)
            scroller.close()
        }
    }

    private fun event(action: Int, y: Float): MotionEvent = MotionEvent.obtain(0L, 0L, action, 24f, y, 0)
}
