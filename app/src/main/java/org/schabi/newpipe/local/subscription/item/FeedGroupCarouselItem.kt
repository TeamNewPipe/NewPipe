package org.schabi.newpipe.local.subscription.item

import android.os.Parcelable
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.local.subscription.SubscriptionGridColumns
import org.schabi.newpipe.local.subscription.SubscriptionGridSpanPolicy
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ThemeHelper.getGridSpanCount

class FeedGroupCarouselItem(
    private val carouselAdapter: GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>,
    var listViewMode: Boolean
) : BindableItem<FeedItemCarouselBinding>() {
    companion object {
        const val PAYLOAD_UPDATE_LIST_VIEW_MODE = 2
        const val PAYLOAD_UPDATE_GRID_COLUMNS = 3
    }

    private var carouselLayoutManager: LinearLayoutManager? = null
    private var listState: Parcelable? = null

    override fun getLayout() = R.layout.feed_item_carousel

    fun onSaveInstanceState(): Parcelable? {
        // Saving a snapshot must not queue it for a later rebind of the still-visible list.
        return carouselLayoutManager?.onSaveInstanceState() ?: listState
    }

    fun onRestoreInstanceState(state: Parcelable?) {
        carouselLayoutManager?.onRestoreInstanceState(state)
        listState = state
    }

    override fun initializeViewBinding(view: View): FeedItemCarouselBinding {
        val viewBinding = FeedItemCarouselBinding.bind(view)
        updateViewMode(viewBinding)
        return viewBinding
    }

    override fun bind(
        viewBinding: FeedItemCarouselBinding,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (
            payloads.contains(PAYLOAD_UPDATE_LIST_VIEW_MODE) ||
            payloads.contains(PAYLOAD_UPDATE_GRID_COLUMNS)
        ) {
            updateViewMode(viewBinding)
            return
        }

        super.bind(viewBinding, position, payloads)
    }

    override fun bind(viewBinding: FeedItemCarouselBinding, position: Int) {
        updateViewMode(viewBinding)
    }

    override fun unbind(viewHolder: GroupieViewHolder<FeedItemCarouselBinding>) {
        super.unbind(viewHolder)
        val boundManager = viewHolder.binding.recyclerView.layoutManager as? LinearLayoutManager
        if (boundManager === carouselLayoutManager) listState = boundManager?.onSaveInstanceState()
    }

    private fun updateViewMode(viewBinding: FeedItemCarouselBinding) {
        val context = viewBinding.root.context
        val spanCount = SubscriptionGridSpanPolicy.resolve(
            SubscriptionGridColumns.get(context),
            getGridSpanCount(context, DeviceUtils.dpToPx(112, context))
        )
        val current = carouselLayoutManager
        val sameLayout = if (listViewMode) {
            current != null && current !is GridLayoutManager
        } else {
            current is GridLayoutManager && current.spanCount == spanCount
        }
        if (!sameLayout || viewBinding.recyclerView.layoutManager !== current) {
            val savedState = listState ?: current?.onSaveInstanceState()
            carouselLayoutManager = if (listViewMode) LinearLayoutManager(context) else GridLayoutManager(context, spanCount)
            viewBinding.recyclerView.layoutManager = carouselLayoutManager
            listState = savedState
        }
        if (viewBinding.recyclerView.adapter !== carouselAdapter) {
            viewBinding.recyclerView.adapter = carouselAdapter
        }
        listState?.let {
            carouselLayoutManager?.onRestoreInstanceState(it)
            listState = null
        }
    }
}
