package org.schabi.newpipe.local.subscription.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.TouchCallback
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import icepick.Icepick
import icepick.State
import java.util.Collections
import kotlinx.android.synthetic.main.dialog_feed_group_reorder.confirm_button
import kotlinx.android.synthetic.main.dialog_feed_group_reorder.feed_groups_list
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialogViewModel.DialogEvent.ProcessingEvent
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialogViewModel.DialogEvent.SuccessEvent
import org.schabi.newpipe.local.subscription.item.FeedGroupReorderItem
import org.schabi.newpipe.util.ThemeHelper

class FeedGroupReorderDialog : DialogFragment() {
    private lateinit var viewModel: FeedGroupReorderDialogViewModel

    @State
    @JvmField
    var groupOrderedIdList = ArrayList<Long>()
    private val groupAdapter = GroupAdapter<GroupieViewHolder>()
    private val itemTouchHelper = ItemTouchHelper(getItemTouchCallback())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_feed_group_reorder, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(FeedGroupReorderDialogViewModel::class.java)
        viewModel.groupsLiveData.observe(viewLifecycleOwner, Observer(::handleGroups))
        viewModel.dialogEventLiveData.observe(viewLifecycleOwner, Observer {
            when (it) {
                ProcessingEvent -> disableInput()
                SuccessEvent -> dismiss()
            }
        })

        feed_groups_list.layoutManager = LinearLayoutManager(requireContext())
        feed_groups_list.adapter = groupAdapter
        itemTouchHelper.attachToRecyclerView(feed_groups_list)

        confirm_button.setOnClickListener {
            viewModel.updateOrder(groupOrderedIdList)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    private fun handleGroups(list: List<FeedGroupEntity>) {
        val groupList: List<FeedGroupEntity>

        if (groupOrderedIdList.isEmpty()) {
            groupList = list
            groupOrderedIdList.addAll(groupList.map { it.uid })
        } else {
            groupList = list.sortedBy { groupOrderedIdList.indexOf(it.uid) }
        }

        groupAdapter.update(groupList.map { FeedGroupReorderItem(it, itemTouchHelper) })
    }

    private fun disableInput() {
        confirm_button?.isEnabled = false
        isCancelable = false
    }

    private fun getItemTouchCallback(): SimpleCallback {
        return object : TouchCallback() {

            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val sourceIndex = source.adapterPosition
                val targetIndex = target.adapterPosition

                groupAdapter.notifyItemMoved(sourceIndex, targetIndex)
                Collections.swap(groupOrderedIdList, sourceIndex, targetIndex)

                return true
            }

            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {}
        }
    }
}
