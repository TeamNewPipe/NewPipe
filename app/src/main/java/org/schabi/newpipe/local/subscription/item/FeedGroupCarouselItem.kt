package org.schabi.newpipe.local.subscription.item

import android.content.Context
import android.os.Parcelable
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import kotlinx.android.synthetic.main.feed_item_carousel.recycler_view
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.decoration.FeedGroupCarouselDecoration

class FeedGroupCarouselItem(context: Context, private val carouselAdapter: GroupAdapter<GroupieViewHolder>) : Item() {
    private val feedGroupCarouselDecoration = FeedGroupCarouselDecoration(context)

    private var linearLayoutManager: LinearLayoutManager? = null
    private var listState: Parcelable? = null

    override fun getLayout() = R.layout.feed_item_carousel

    fun onSaveInstanceState(): Parcelable? {
        listState = linearLayoutManager?.onSaveInstanceState()
        return listState
    }

    fun onRestoreInstanceState(state: Parcelable?) {
        linearLayoutManager?.onRestoreInstanceState(state)
        listState = state
    }

    override fun createViewHolder(itemView: View): GroupieViewHolder {
        val viewHolder = super.createViewHolder(itemView)

        linearLayoutManager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false)

        viewHolder.recycler_view.apply {
            layoutManager = linearLayoutManager
            adapter = carouselAdapter
            addItemDecoration(feedGroupCarouselDecoration)
        }

        return viewHolder
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        viewHolder.recycler_view.apply { adapter = carouselAdapter }
        linearLayoutManager?.onRestoreInstanceState(listState)
    }

    override fun unbind(viewHolder: GroupieViewHolder) {
        super.unbind(viewHolder)

        listState = linearLayoutManager?.onSaveInstanceState()
    }
}
