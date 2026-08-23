package org.schabi.newpipe.local.subscription.item

import android.content.Context
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.local.subscription.decoration.FeedGroupCarouselDecoration
import org.schabi.newpipe.local.subscription.decoration.FeedGroupGridDecoration

class FeedGroupCarouselItem(
    private val context: Context,
    private val carouselAdapter: GroupAdapter<GroupieViewHolder<*>>,
    private val useGridLayout: Boolean = false,
    private val gridSpanCount: Int = 2
) : BindableItem<FeedItemCarouselBinding>() {

    private val feedGroupCarouselDecoration = FeedGroupCarouselDecoration(context)
    private val feedGroupGridDecoration = FeedGroupGridDecoration(context)

    private val leftMarginGridPx: Int by lazy {
        (12 * context.resources.displayMetrics.density).toInt()
    }


    private var layoutManager: RecyclerView.LayoutManager? = null
    private var listState: Parcelable? = null

    override fun getLayout() = R.layout.feed_item_carousel

    fun onSaveInstanceState(): Parcelable? {
        listState = layoutManager?.onSaveInstanceState()
        return listState
    }

    fun onRestoreInstanceState(state: Parcelable?) {
        layoutManager?.onRestoreInstanceState(state)
        listState = state
    }

    override fun initializeViewBinding(view: View): FeedItemCarouselBinding {
        val viewHolder = FeedItemCarouselBinding.bind(view)

        layoutManager = if (useGridLayout) {
            GridLayoutManager(view.context, gridSpanCount)
        } else {
            LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false)
        }

        viewHolder.recyclerView.apply {
            layoutManager = this@FeedGroupCarouselItem.layoutManager
            adapter = carouselAdapter

            // Remove existing decorations
            while (itemDecorationCount > 0) {
                removeItemDecorationAt(0)
            }

            // Add appropriate decoration
            if (useGridLayout) {
                addItemDecoration(feedGroupGridDecoration)
            } else {
                addItemDecoration(feedGroupCarouselDecoration)
            }
        }

        return viewHolder
    }

    override fun bind(viewBinding: FeedItemCarouselBinding, position: Int) {
        // Adjust the margin of the root layout based on whether it's a grid or not.
        // This is done in bind() to correctly handle view recycling.
        (viewBinding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.leftMargin = if (useGridLayout) {
                leftMarginGridPx
            } else {
                0 // Reset margin for non-grid items
            }
            viewBinding.root.layoutParams = params
        }
        viewBinding.recyclerView.adapter = carouselAdapter
        layoutManager?.onRestoreInstanceState(listState)
    }

    override fun unbind(viewHolder: GroupieViewHolder<FeedItemCarouselBinding>) {
        super.unbind(viewHolder)
        listState = layoutManager?.onSaveInstanceState()
    }
}
