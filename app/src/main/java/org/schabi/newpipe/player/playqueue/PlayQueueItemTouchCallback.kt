package org.schabi.newpipe.player.playqueue

import androidx.core.math.MathUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sign

abstract class PlayQueueItemTouchCallback() : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT) {
    abstract fun onMove(sourceIndex: Int, targetIndex: Int)
    abstract fun onSwiped(index: Int)
    public override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView,
                                                     viewSize: Int,
                                                     viewSizeOutOfBounds: Int,
                                                     totalSize: Int,
                                                     msSinceStartScroll: Long): Int {
        val standardSpeed: Int = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll)
        val clampedAbsVelocity: Int = MathUtils.clamp(abs(standardSpeed.toDouble()),
                MINIMUM_INITIAL_DRAG_VELOCITY, MAXIMUM_INITIAL_DRAG_VELOCITY)
        return clampedAbsVelocity * sign(viewSizeOutOfBounds.toDouble()).toInt()
    }

    public override fun onMove(recyclerView: RecyclerView,
                               source: RecyclerView.ViewHolder,
                               target: RecyclerView.ViewHolder): Boolean {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false
        }
        val sourceIndex: Int = source.getLayoutPosition()
        val targetIndex: Int = target.getLayoutPosition()
        onMove(sourceIndex, targetIndex)
        return true
    }

    public override fun isLongPressDragEnabled(): Boolean {
        return false
    }

    public override fun isItemViewSwipeEnabled(): Boolean {
        return true
    }

    public override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
        onSwiped(viewHolder.getBindingAdapterPosition())
    }

    companion object {
        private val MINIMUM_INITIAL_DRAG_VELOCITY: Int = 10
        private val MAXIMUM_INITIAL_DRAG_VELOCITY: Int = 25
    }
}
