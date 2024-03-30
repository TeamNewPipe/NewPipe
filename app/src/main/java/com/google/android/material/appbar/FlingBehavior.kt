package com.google.android.material.appbar

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.schabi.newpipe.R
import java.lang.reflect.Field
import java.util.List

// See https://stackoverflow.com/questions/56849221#57997489
class FlingBehavior(context: Context?, attrs: AttributeSet?) : AppBarLayout.Behavior(context, attrs) {
    private val focusScrollRect = Rect()
    private var allowScroll = true
    private val globalRect = Rect()
    private val skipInterceptionOfElements = List.of(
            R.id.itemsListPanel, R.id.playbackSeekBar,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton)

    override fun onRequestChildRectangleOnScreen(
            coordinatorLayout: CoordinatorLayout, child: AppBarLayout,
            rectangle: Rect, immediate: Boolean): Boolean {
        focusScrollRect.set(rectangle)
        coordinatorLayout.offsetDescendantRectToMyCoords(child, focusScrollRect)
        val height = coordinatorLayout.height
        if (focusScrollRect.top <= 0 && focusScrollRect.bottom >= height) {
            // the child is too big to fit inside ourselves completely, ignore request
            return false
        }
        val dy: Int
        dy = if (focusScrollRect.bottom > height) {
            focusScrollRect.top
        } else if (focusScrollRect.top < 0) {
            // scrolling up
            -(height - focusScrollRect.bottom)
        } else {
            // nothing to do
            return false
        }
        val consumed = scroll(coordinatorLayout, child, dy, getMaxDragOffset(child), 0)
        return consumed == dy
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout,
                                       child: AppBarLayout,
                                       ev: MotionEvent): Boolean {
        for (element in skipInterceptionOfElements) {
            val view = child.findViewById<View>(element)
            if (view != null) {
                val visible = view.getGlobalVisibleRect(globalRect)
                if (visible && globalRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    allowScroll = false
                    return false
                }
            }
        }
        allowScroll = true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // remove reference to old nested scrolling child
                resetNestedScrollingChild()
                // Stop fling when your finger touches the screen
                stopAppBarLayoutFling()
            }

            else -> {}
        }
        return super.onInterceptTouchEvent(parent, child, ev)
    }

    override fun onStartNestedScroll(parent: CoordinatorLayout,
                                     child: AppBarLayout,
                                     directTargetChild: View,
                                     target: View,
                                     nestedScrollAxes: Int,
                                     type: Int): Boolean {
        return allowScroll && super.onStartNestedScroll(
                parent, child, directTargetChild, target, nestedScrollAxes, type)
    }

    override fun onNestedFling(coordinatorLayout: CoordinatorLayout,
                               child: AppBarLayout,
                               target: View, velocityX: Float,
                               velocityY: Float, consumed: Boolean): Boolean {
        return allowScroll && super.onNestedFling(
                coordinatorLayout, child, target, velocityX, velocityY, consumed)
    }

    private val scrollerField: OverScroller?
        private get() {
            try {
                val headerBehaviorType: Class<*>? = this.javaClass
                        .superclass.superclass.superclass
                if (headerBehaviorType != null) {
                    val field = headerBehaviorType.getDeclaredField("scroller")
                    field.isAccessible = true
                    return field[this] as OverScroller
                }
            } catch (e: NoSuchFieldException) {
                // ?
            } catch (e: IllegalAccessException) {
            }
            return null
        }
    private val lastNestedScrollingChildRefField: Field?
        private get() {
            try {
                val headerBehaviorType: Class<*>? = this.javaClass.superclass.superclass
                if (headerBehaviorType != null) {
                    val field = headerBehaviorType.getDeclaredField("lastNestedScrollingChildRef")
                    field.isAccessible = true
                    return field
                }
            } catch (e: NoSuchFieldException) {
                // ?
            }
            return null
        }

    private fun resetNestedScrollingChild() {
        val field = lastNestedScrollingChildRefField
        if (field != null) {
            try {
                val value = field[this]
                if (value != null) {
                    field[this] = null
                }
            } catch (e: IllegalAccessException) {
                // ?
            }
        }
    }

    private fun stopAppBarLayoutFling() {
        val scroller = scrollerField
        scroller?.forceFinished(true)
    }
}
