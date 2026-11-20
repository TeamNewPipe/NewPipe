package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentGroupChannelsBinding
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.history.ListFastScroller
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.util.ExpandableSearchViewHelper
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture

class GroupChannelsFragment : Fragment() {
    private var _binding: FragmentGroupChannelsBinding? = null
    private val binding get() = _binding!!
    private val adapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var channelActions: SubscriptionChannelActions
    private lateinit var viewModel: GroupChannelsViewModel

    private var searchQuery = ""
    private var searchExpanded = false
    private var gridMode = false
    private var fastScroller: ListFastScroller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchQuery = savedInstanceState?.getString(KEY_SEARCH_QUERY).orEmpty()
        searchExpanded = savedInstanceState?.getBoolean(KEY_SEARCH_EXPANDED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
        outState.putBoolean(KEY_SEARCH_EXPANDED, searchExpanded)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_group_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentGroupChannelsBinding.bind(view)
        channelActions = SubscriptionChannelActions(this, SubscriptionManager(requireContext()))
        val groupId = arguments?.getLong(KEY_GROUP_ID) ?: FeedGroupEntity.GROUP_ALL_ID
        val groupName = arguments?.getString(KEY_GROUP_NAME).orEmpty()

        requireActivity().title = if (groupName.isBlank()) {
            getString(R.string.channels)
        } else {
            groupName
        }

        gridMode = SubscriptionViewModel.shouldUseGridForSubscription(requireContext())
        binding.itemsList.layoutManager = if (gridMode) {
            GridLayoutManagerHelper.create(
                binding.itemsList,
                resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width),
                SubscriptionGridColumns.get(requireContext())
            ) { spanCount ->
                adapter.spanCount = spanCount
                adapter.spanSizeLookup
            }
        } else {
            adapter.spanCount = 1
            GridLayoutManager(requireContext(), 1).apply {
                spanSizeLookup = adapter.spanSizeLookup
            }
        }
        binding.itemsList.adapter = adapter
        fastScroller = ListFastScroller.attach(binding.itemsList)

        viewModel = ViewModelProvider(this)[GroupChannelsViewModel::class.java]
        binding.errorPanel.errorRetryButton.apply {
            isVisible = true
            setOnClickListener { viewModel.load(groupId) }
        }
        viewModel.state.observe(viewLifecycleOwner, ::render)
        (requireActivity() as MenuHost).addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_group_channels, menu)
                    ExpandableSearchViewHelper.configure(
                        menu.findItem(R.id.menu_item_search_content),
                        getString(R.string.contextual_search_hint, groupName.ifBlank { getString(R.string.channels) }),
                        searchQuery,
                        searchExpanded,
                        { query ->
                            searchQuery = ContextualSearchHelper.normalizeQuery(query)
                            viewModel.state.value?.let(::render)
                        },
                        { expanded -> searchExpanded = expanded }
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
        viewModel.load(groupId)
    }

    private fun render(state: GroupChannelsViewModel.State) {
        val currentBinding = _binding ?: return
        val channels = state.channels.filter { SubscriptionSearch.matches(searchQuery, it) }
        currentBinding.loadingProgressBar.isVisible = state.loading
        currentBinding.itemsList.isVisible = !state.loading && state.error == null && channels.isNotEmpty()
        currentBinding.emptyStateView.root.isVisible = !state.loading && state.error == null && channels.isEmpty()
        currentBinding.emptyStateView.emptyStateMessage.setText(
            if (ContextualSearchHelper.isActive(searchQuery)) R.string.search_no_results else R.string.empty_list_message
        )
        currentBinding.errorPanel.root.isVisible = state.error != null
        adapter.update(
            channels.map { subscription ->
                ChannelItem(
                    subscription.toChannelInfoItem(),
                    subscription.uid,
                    if (gridMode) ChannelItem.ItemVersion.GRID else ChannelItem.ItemVersion.MINI
                ).apply {
                    gesturesListener = object : OnClickGesture<ChannelInfoItem> {
                        override fun selected(
                            selectedItem: ChannelInfoItem
                        ) = NavigationHelper.openChannelFragment(
                            parentFragmentManager,
                            selectedItem.serviceId,
                            selectedItem.url,
                            selectedItem.name
                        )

                        override fun held(selectedItem: ChannelInfoItem) = channelActions.show(selectedItem)
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        fastScroller?.close()
        fastScroller = null
        channelActions.close()
        binding.itemsList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_SEARCH_EXPANDED = "search_expanded"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_GROUP_NAME = "group_name"

        fun newInstance(groupId: Long, groupName: String): GroupChannelsFragment {
            return GroupChannelsFragment().apply {
                arguments = bundleOf(KEY_GROUP_ID to groupId, KEY_GROUP_NAME to groupName)
            }
        }
    }
}
