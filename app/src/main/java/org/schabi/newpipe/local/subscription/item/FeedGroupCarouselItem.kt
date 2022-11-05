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
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ThemeHelper.getGridSpanCount

class FeedGroupCarouselItem(
    private val carouselAdapter: GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>,
    var listViewMode: Boolean
) : BindableItem<FeedItemCarouselBinding>() {
    companion object {
        const val PAYLOAD_UPDATE_LIST_VIEW_MODE = 2
    }

    private var carouselLayoutManager: LinearLayoutManager? = null
    private var listState: Parcelable? = null

    override fun getLayout() = R.layout.feed_item_carousel

    fun onSaveInstanceState(): Parcelable? {
        listState = carouselLayoutManager?.onSaveInstanceState()
        return listState
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
        if (payloads.contains(PAYLOAD_UPDATE_LIST_VIEW_MODE)) {
            updateViewMode(viewBinding)
            return
        }

        super.bind(viewBinding, position, payloads)
    }

    override fun bind(viewBinding: FeedItemCarouselBinding, position: Int) {
        viewBinding.recyclerView.apply { adapter = carouselAdapter }
        carouselLayoutManager?.onRestoreInstanceState(listState)
    }

    override fun unbind(viewHolder: GroupieViewHolder<FeedItemCarouselBinding>) {
        super.unbind(viewHolder)
        listState = carouselLayoutManager?.onSaveInstanceState()
    }

    private fun updateViewMode(viewBinding: FeedItemCarouselBinding) {
        viewBinding.recyclerView.apply { adapter = carouselAdapter }

        val context = viewBinding.root.context
        carouselLayoutManager = if (listViewMode) {
            LinearLayoutManager(context)
        } else {
            GridLayoutManager(context, getGridSpanCount(context, DeviceUtils.dpToPx(112, context)))
        }

        viewBinding.recyclerView.apply {
            layoutManager = carouselLayoutManager
            adapter = carouselAdapter
        }
    }
}
