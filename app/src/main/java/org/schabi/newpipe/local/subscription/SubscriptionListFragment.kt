package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.GroupieViewHolder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionListBinding
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.local.feed.FeedState
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.EmptyPlaceholderItem
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.ThemeHelper.getGridSpanCountChannels
import org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout
import org.schabi.newpipe.util.external_communication.ShareUtils

class SubscriptionListFragment : BaseStateFragment<FeedState>() {
    private var _binding: FragmentSubscriptionListBinding? = null
    private val binding get() = _binding!!

    private lateinit var subscriptionManager: SubscriptionManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val groupAdapter = GroupAdapter<GroupieViewHolder<*>>()
    private val subscriptionsSection = Section()

    private var groupId: Long = FeedGroupEntity.GROUP_ALL_ID
    private var groupName: String = ""
    private var currentQuery: String = ""
    private var searchView: SearchView? = null

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_GROUP_NAME = "group_name"
        @JvmStatic
        fun newInstance(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, groupName: String = ""): SubscriptionListFragment {
            return SubscriptionListFragment().apply {
                arguments = bundleOf(
                    ARG_GROUP_ID to groupId,
                    ARG_GROUP_NAME to groupName
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        arguments?.let {
            groupId = it.getLong(ARG_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
            groupName = it.getString(ARG_GROUP_NAME, "")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActionBar()
        setupRecyclerView()
        loadSubscriptions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_subscription_list, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? SearchView

        searchView?.apply {
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    currentQuery = newText ?: ""
                    loadSubscriptions()
                    return true
                }
            })

            // Restore query if there was one
            if (currentQuery.isNotEmpty()) {
                searchItem?.expandActionView()
                setQuery(currentQuery, false)
            }
        }
    }

    private fun setupActionBar() {
        activity?.supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = when {
                groupName.isNotEmpty() -> groupName
                groupId == FeedGroupEntity.GROUP_ALL_ID -> getString(R.string.tab_subscriptions)
                else -> getString(R.string.tab_subscriptions)
            }
        }
    }

    private fun setupRecyclerView() {
        // Clear any existing items first to avoid duplicate observer registration
        groupAdapter.clear()

        // Set up placeholder for empty state
        subscriptionsSection.setPlaceholder(EmptyPlaceholderItem())
        subscriptionsSection.setHideWhenEmpty(true)

        // Add section to adapter
        groupAdapter.add(subscriptionsSection)

        // Configure layout manager
        val useGrid = shouldUseGridLayout(context)
        binding.recyclerView.layoutManager = if (useGrid) {
            GridLayoutManager(requireContext(), getGridSpanCountChannels(context))
        } else {
            LinearLayoutManager(requireContext())
        }

        binding.recyclerView.adapter = groupAdapter
    }


    private fun loadSubscriptions() {
        disposables.clear()

        disposables.add(
            subscriptionManager.getSubscriptions(
                currentGroupId = groupId,
                filterQuery = currentQuery,
                showOnlyUngrouped = false
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { subscriptions ->
                        val channelItems = subscriptions.map { entity ->
                            ChannelItem(
                                entity.toChannelInfoItem(),
                                entity.uid,
                                ChannelItem.ItemVersion.MINI
                            )
                        }
                        updateSubscriptions(channelItems)
                    },
                    { error ->
                        Toast.makeText(
                            context,
                            "Error loading subscriptions: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
        )
    }

    private fun updateSubscriptions(items: List<ChannelItem>) {
        val shouldUseGrid = shouldUseGridLayout(context)

        items.forEach { item ->
            item.gesturesListener = channelClickListener
            item.itemVersion = if (shouldUseGrid) {
                ChannelItem.ItemVersion.GRID
            } else {
                ChannelItem.ItemVersion.MINI
            }
        }

        subscriptionsSection.update(items)
    }

    private val channelClickListener = object : OnClickGesture<ChannelInfoItem>() {
        override fun selected(selectedItem: ChannelInfoItem) {
            // Open channel fragment
            NavigationHelper.openChannelFragment(
                parentFragmentManager,
                selectedItem.serviceId,
                selectedItem.url,
                selectedItem.name
            )
        }

        override fun held(selectedItem: ChannelInfoItem) {
            // Show context menu
            showChannelLongPressDialog(selectedItem)
        }
    }

    private fun showChannelLongPressDialog(channel: ChannelInfoItem) {
        val commandsList = mutableListOf(
            getString(R.string.share),
            getString(R.string.open_in_browser)
        )

        // Add "Remove from group" option if we're viewing a specific group (not "All")
        if (groupId != FeedGroupEntity.GROUP_ALL_ID) {
            commandsList.add(getString(R.string.remove_from_group))
        }

        commandsList.add(getString(R.string.unsubscribe))

        val commands = commandsList.toTypedArray()

        val actions = DialogInterface.OnClickListener { _, which ->
            when (which) {
                0 -> ShareUtils.shareText(
                    requireContext(),
                    channel.name,
                    channel.url,
                    channel.thumbnailUrl
                )
                1 -> ShareUtils.openUrlInBrowser(requireContext(), channel.url)
                2 -> {
                    if (groupId != FeedGroupEntity.GROUP_ALL_ID) {
                        // Remove from group
                        removeChannelFromGroup(channel)
                    } else {
                        // Unsubscribe
                        unsubscribeChannel(channel)
                    }
                }
                3 -> {
                    if (groupId != FeedGroupEntity.GROUP_ALL_ID) {
                        // Unsubscribe (when "Remove from group" is shown)
                        unsubscribeChannel(channel)
                    }
                }
            }
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(requireContext()))
        dialogTitleBinding.itemTitleView.text = channel.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands, actions)
            .create()
            .show()
    }

    private fun removeChannelFromGroup(channel: ChannelInfoItem) {
        val feedGroupDAO = NewPipeDatabase.getInstance(requireContext()).feedGroupDAO()

        disposables.add(
            subscriptionManager.subscriptionTable().getSubscription(channel.serviceId, channel.url)
                .flatMapCompletable { subscriptionEntity ->
                    feedGroupDAO.getSubscriptionIdsFor(groupId)
                        .firstOrError()
                        .flatMapCompletable { currentSubscriptionIds ->
                            Completable.fromAction {
                                // Remove this subscription from the list
                                val updatedIds = currentSubscriptionIds.filter { it != subscriptionEntity.uid }

                                // Update the group with the new subscription list
                                feedGroupDAO.updateSubscriptionsForGroup(groupId, updatedIds)
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.channel_removed_from_group),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Reload subscriptions to reflect the change
                        loadSubscriptions()
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            "Error removing from group: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
        )
    }

    private fun unsubscribeChannel(channel: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteSubscription(channel.serviceId, channel.url)
                .subscribe(
                    {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.channel_unsubscribed),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Reload subscriptions to reflect the change
                        loadSubscriptions()
                    },
                    { error ->
                        Toast.makeText(
                            requireContext(),
                            "Error unsubscribing: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
        )
    }
}
