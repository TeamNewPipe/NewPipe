package org.schabi.newpipe.local.subscription

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.evernote.android.state.State
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.GroupieViewHolder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_ALL_ID
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.feed.SavedSearchFeedManager
import org.schabi.newpipe.local.history.ListFastScroller
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.search.ContextualSearchable
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog
import org.schabi.newpipe.local.subscription.dialog.SubscriptionLayoutDialog
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCarouselItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem
import org.schabi.newpipe.local.subscription.item.GroupsHeader
import org.schabi.newpipe.local.subscription.item.Header
import org.schabi.newpipe.local.subscription.item.ImportSubscriptionsHintPlaceholderItem
import org.schabi.newpipe.local.subscription.item.SearchNoResultsPlaceholderItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.ServiceHelper

class SubscriptionFragment : BaseStateFragment<SubscriptionState>(), ContextualSearchable {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var activeProfileId: String
    private lateinit var channelActions: SubscriptionChannelActions
    private lateinit var bulkActions: SubscriptionBulkActions
    private lateinit var groupActions: SubscriptionGroupActions
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var importExportHelper: SubscriptionsImportExportHelper
    private lateinit var savedSearchFeedManager: SavedSearchFeedManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val groupAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var carouselAdapter: GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>
    private lateinit var feedGroupsCarousel: FeedGroupCarouselItem
    private lateinit var feedGroupsSortMenuItem: GroupsHeader
    private lateinit var feedGroupsSection: Section
    private var feedGroupsCollapsed = false
    private val subscriptionsSection = Section()
    private var contextualSearchQuery = ""
    private var sortingEnabled = false
    private var sortController: SubscriptionSortController? = null
    private var bulkUnsubscribeMenuItem: MenuItem? = null
    private var pendingBrowseAnchor: SubscriptionScrollAnchor? = null
    private var countDisposable: io.reactivex.rxjava3.disposables.Disposable? = null
    private var fastScroller: ListFastScroller? = null

    @State
    @JvmField
    var itemsListState: Parcelable? = null

    @State
    @JvmField
    var feedGroupsCarouselState: Parcelable? = null

    init {
        setHasOptionsMenu(true)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activeProfileId = ProfileManager.getActiveProfileId(context)
        feedGroupsCollapsed = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(SubscriptionGroupsCollapse.key(activeProfileId), false)
        subscriptionManager = SubscriptionManager(context, activeProfileId)
        channelActions = SubscriptionChannelActions(this, subscriptionManager)
        bulkActions = SubscriptionBulkActions(this, subscriptionManager)
        groupActions = SubscriptionGroupActions(this, subscriptionManager)
        importExportHelper = SubscriptionsImportExportHelper(this)
        savedSearchFeedManager = SavedSearchFeedManager(context, activeProfileId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_subscription, container, false)
    }

    override fun onPause() {
        super.onPause()
        itemsListState = binding.itemsList.layoutManager?.onSaveInstanceState()
        feedGroupsCarouselState = feedGroupsCarousel.onSaveInstanceState()
    }

    override fun onDestroyView() {
        channelActions.close()
        bulkActions.close()
        groupActions.close()
        sortController?.close()
        sortController = null
        bulkUnsubscribeMenuItem = null
        pendingBrowseAnchor = null
        countDisposable?.dispose()
        countDisposable = null
        fastScroller?.close()
        fastScroller = null
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("subscription_sort_enabled", sortingEnabled)
        outState.putString("subscription_sort_profile", activeProfileId)
        sortController?.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    // ////////////////////////////////////////////////////////////////////////
    // Menu
    // ////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.tab_subscriptions)

        bulkUnsubscribeMenuItem = setClickListenerToMenuItem(menu.add(R.string.subscription_bulk_unsubscribe)) {
            if (sortingEnabled) {
                bulkActions.confirm(sortController?.selectedChannels().orEmpty())
            } else {
                bulkActions.show(contextualSearchQuery)
            }
        }
        updateUnsubscribeAction()

        setClickListenerToMenuItem(menu.add(R.string.saved_search_feeds)) {
            showSavedSearchFeedsDialog()
        }.setIcon(R.drawable.ic_search)
        setClickListenerToMenuItem(menu.add(R.string.subscription_arrange)) {
            SubscriptionLayoutDialog.newInstance(activeProfileId).show(parentFragmentManager, "subscription_layout")
        }
        menu.add(R.string.subscription_sort_enable).apply {
            isCheckable = true
            isChecked = sortingEnabled
            setOnMenuItemClickListener {
                toggleSorting()
                true
            }
        }
        setClickListenerToMenuItem(menu.add(R.string.subscription_separator_add)) {
            sortController?.editSeparator()
        }.isVisible = sortingEnabled
        if (!sortingEnabled) buildGridColumnsMenu(menu)
        buildImportExportMenu(menu)
    }

    private fun showSavedSearchFeedsDialog() {
        disposables.add(
            savedSearchFeedManager.getAll()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { feeds ->
                        if (feeds.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                R.string.no_saved_search_feeds,
                                Toast.LENGTH_LONG
                            ).show()
                            return@subscribe
                        }

                        val labels = feeds.map { feed ->
                            getString(
                                R.string.saved_search_feed_item,
                                feed.name,
                                feed.query
                            )
                        }.toTypedArray()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.saved_search_feeds)
                            .setItems(labels) { _, index ->
                                NavigationHelper.openSavedSearchFeed(fm, feeds[index])
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    },
                    {
                        Toast.makeText(
                            requireContext(),
                            R.string.saved_search_feed_load_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
        )
    }

    private fun buildGridColumnsMenu(menu: Menu) {
        if (!SubscriptionViewModel.shouldUseGridForSubscription(requireContext())) return

        val submenu = menu.addSubMenu(R.string.grid_columns)
        SubscriptionGridColumns.presets.forEach { columns ->
            val label = if (columns == SubscriptionGridColumns.AUTO) {
                getString(R.string.auto)
            } else {
                columns.toString()
            }
            addMenuItemToSubmenu(submenu, label) {
                SubscriptionGridColumns.set(requireContext(), columns)
                applySubscriptionLayout()
                if (::feedGroupsCarousel.isInitialized) {
                    feedGroupsCarousel.notifyChanged(
                        FeedGroupCarouselItem.PAYLOAD_UPDATE_GRID_COLUMNS
                    )
                }
            }
        }
    }

    private fun buildImportExportMenu(menu: Menu) {
        // -- Import --
        val importSubMenu = menu.addSubMenu(R.string.import_from)

        addMenuItemToSubmenu(importSubMenu, R.string.previous_export) { importExportHelper.onImportPreviousSelected() }
            .setIcon(R.drawable.ic_backup)

        for (service in ServiceHelper.getVisibleServices()) {
            val subscriptionExtractor = service.subscriptionExtractor ?: continue

            val supportedSources = subscriptionExtractor.supportedSources
            if (supportedSources.isEmpty()) continue

            addMenuItemToSubmenu(importSubMenu, service.serviceInfo.name) {
                onImportFromServiceSelected(service.serviceId)
            }
                .setIcon(ServiceHelper.getIcon(service.serviceId))
        }

        // -- Export --
        val exportSubMenu = menu.addSubMenu(R.string.export_to)

        addMenuItemToSubmenu(exportSubMenu, R.string.file) { importExportHelper.onExportSelected() }
            .setIcon(R.drawable.ic_save)
    }

    private fun addMenuItemToSubmenu(
        subMenu: SubMenu,
        @StringRes title: Int,
        onClick: Runnable
    ): MenuItem {
        return setClickListenerToMenuItem(subMenu.add(title), onClick)
    }

    private fun addMenuItemToSubmenu(
        subMenu: SubMenu,
        title: String,
        onClick: Runnable
    ): MenuItem {
        return setClickListenerToMenuItem(subMenu.add(title), onClick)
    }

    private fun setClickListenerToMenuItem(
        menuItem: MenuItem,
        onClick: Runnable
    ): MenuItem {
        menuItem.setOnMenuItemClickListener {
            onClick.run()
            true
        }
        return menuItem
    }

    private fun onImportFromServiceSelected(serviceId: Int) {
        val fragmentManager = fm
        NavigationHelper.openSubscriptionsImportFragment(fragmentManager, serviceId)
    }

    private fun openReorderDialog() {
        FeedGroupReorderDialog().show(parentFragmentManager, null)
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        _binding = FragmentSubscriptionBinding.bind(rootView)
        sortingEnabled = savedInstanceState?.getString("subscription_sort_profile") == activeProfileId &&
            savedInstanceState?.getBoolean("subscription_sort_enabled") == true

        applySubscriptionLayout()
        binding.itemsList.adapter = groupAdapter
        // Keep the drag surface below the collapsible group header so its
        // right-hand expand button remains tappable even for long subscription lists.
        fastScroller = ListFastScroller.attach(binding.itemsList, topClearanceDp = 56)
        binding.itemsList.itemAnimator = null

        viewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]
        viewModel.setProfile(activeProfileId)
        viewModel.setFilterQuery(contextualSearchQuery)
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(this::handleResult) }
        viewModel.feedGroupsLiveData.observe(viewLifecycleOwner) {
            it?.let { (groups, listViewMode) ->
                handleFeedGroups(groups, listViewMode)
            }
        }

        setupInitialLayout()
        sortController = SubscriptionSortController(requireContext(), activeProfileId, binding, savedInstanceState) {
            updateUnsubscribeAction()
        }.apply {
            setQuery(contextualSearchQuery)
            setEnabled(sortingEnabled)
            restoreScroll(savedInstanceState)
        }
        updateUnsubscribeAction()
        binding.subscriptionEditToggle.setOnClickListener { toggleSorting() }
        binding.subscriptionEditToggle.setText(if (sortingEnabled) R.string.playlist_edit_done else R.string.playlist_edit_mode)
        countDisposable = subscriptionManager.subscriptions()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ channels ->
                _binding?.subscriptionTotalCount?.text = resources.getQuantityString(
                    R.plurals.subscription_total_count,
                    channels.size,
                    channels.size
                )
            }, { error ->
                org.schabi.newpipe.error.ErrorUtil.showSnackbar(this, ErrorInfo(error, UserAction.SOMETHING_ELSE, "Counting subscriptions"))
            })
        if (sortingEnabled) updateSortingVisibility()
    }

    private fun updateUnsubscribeAction() {
        bulkUnsubscribeMenuItem?.isEnabled = !sortingEnabled || sortController?.selectedChannels()?.isNotEmpty() == true
    }

    private fun toggleSorting() {
        val anchor = if (sortingEnabled) sortController?.scrollAnchor() else browseScrollAnchor()
        sortingEnabled = !sortingEnabled
        sortController?.setEnabled(sortingEnabled)
        updateSortingVisibility()
        if (sortingEnabled) sortController?.scrollTo(anchor) else restoreBrowseScroll(anchor)
        activity.invalidateOptionsMenu()
    }

    private fun browseItemKey(position: Int): String? {
        if (position !in 0 until groupAdapter.itemCount) return null
        return when (val item = groupAdapter.getItem(position)) {
            is ChannelItem -> item.layoutKey
            is org.schabi.newpipe.local.subscription.item.SubscriptionSeparatorItem -> item.entry.key
            else -> null
        }
    }

    private fun browseScrollAnchor(): SubscriptionScrollAnchor? = pendingBrowseAnchor ?: SubscriptionScrollAnchor.capture(binding.itemsList, ::browseItemKey)

    private fun restoreBrowseScroll(anchor: SubscriptionScrollAnchor?) {
        if (anchor == null) return
        val position = (0 until groupAdapter.itemCount).firstOrNull { browseItemKey(it) == anchor.key } ?: return
        // A database emission can arrive before the pending scroll has been laid out.
        // Retain the requested channel rather than recapturing the old visible children.
        pendingBrowseAnchor = anchor
        binding.itemsList.doOnNextLayout {
            if (pendingBrowseAnchor === anchor) pendingBrowseAnchor = null
        }
        (binding.itemsList.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, anchor.offset)
    }

    private fun updateSortingVisibility() {
        binding.subscriptionEditToggle.setText(if (sortingEnabled) R.string.playlist_edit_done else R.string.playlist_edit_mode)
        binding.itemsList.animate().cancel()
        binding.itemsList.alpha = 1f
        binding.itemsList.visibility = if (sortingEnabled) View.GONE else View.VISIBLE
    }

    private fun applySubscriptionLayout() {
        val gridMode = SubscriptionViewModel.shouldUseGridForSubscription(requireContext())
        val fixedColumns = SubscriptionGridColumns.get(requireContext())
        val minimumItemWidth = resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width)
        binding.itemsList.layoutManager = if (gridMode && fixedColumns > 0) {
            groupAdapter.spanCount = fixedColumns
            GridLayoutManager(requireContext(), fixedColumns).apply {
                spanSizeLookup = groupAdapter.spanSizeLookup
            }
        } else if (gridMode) {
            GridLayoutManagerHelper.create(binding.itemsList, minimumItemWidth) { spanCount ->
                groupAdapter.spanCount = spanCount
                groupAdapter.spanSizeLookup
            }
        } else {
            groupAdapter.spanCount = 1
            GridLayoutManager(requireContext(), 1).apply {
                spanSizeLookup = groupAdapter.spanSizeLookup
            }
        }
    }

    private fun setupInitialLayout() {
        Section().apply {
            carouselAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()

            carouselAdapter.setOnItemClickListener { item, _ ->
                when (item) {
                    is FeedGroupCardItem ->
                        NavigationHelper.openFeedFragment(fm, item.groupId, item.name)

                    is FeedGroupCardGridItem ->
                        NavigationHelper.openFeedFragment(fm, item.groupId, item.name)

                    is FeedGroupAddNewItem ->
                        FeedGroupDialog.newInstance().show(fm, null)

                    is FeedGroupAddNewGridItem ->
                        FeedGroupDialog.newInstance().show(fm, null)

                    is FeedGroupImportItem, is FeedGroupImportGridItem ->
                        importExportHelper.onImportPreviousSelected()
                }
            }
            carouselAdapter.setOnItemLongClickListener { item, _ ->
                if ((item is FeedGroupCardItem && item.groupId == GROUP_ALL_ID) ||
                    (item is FeedGroupCardGridItem && item.groupId == GROUP_ALL_ID)
                ) {
                    return@setOnItemLongClickListener false
                }

                when (item) {
                    is FeedGroupCardItem ->
                        groupActions.show(item.groupId, item.name)

                    is FeedGroupCardGridItem ->
                        groupActions.show(item.groupId, item.name)
                }
                return@setOnItemLongClickListener true
            }

            feedGroupsCarousel = FeedGroupCarouselItem(
                carouselAdapter = carouselAdapter,
                listViewMode = viewModel.getListViewMode()
            )

            feedGroupsSortMenuItem = GroupsHeader(
                title = getString(R.string.feed_groups_header_title),
                onSortClicked = ::openReorderDialog,
                onSearchClicked = ::showGroupSearch,
                onToggleListViewModeClicked = ::toggleListViewMode,
                onToggleCollapsedClicked = ::toggleGroupsCollapsed,
                listViewMode = viewModel.getListViewMode(),
                collapsed = feedGroupsCollapsed
            )

            feedGroupsSection = Section(
                feedGroupsSortMenuItem,
                if (feedGroupsCollapsed) emptyList() else listOf(feedGroupsCarousel)
            )
            add(feedGroupsSection)
            groupAdapter.clear()
            groupAdapter.add(this)
        }

        subscriptionsSection.setPlaceholder(ImportSubscriptionsHintPlaceholderItem())
        subscriptionsSection.setHideWhenEmpty(true)

        groupAdapter.add(
            Section(
                Header(getString(R.string.tab_subscriptions)),
                listOf(subscriptionsSection)
            )
        )
    }

    override fun setContextualSearchQuery(query: String) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query)
        subscriptionsSection.setPlaceholder(
            if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                SearchNoResultsPlaceholderItem()
            } else {
                ImportSubscriptionsHintPlaceholderItem()
            }
        )
        if (::viewModel.isInitialized) {
            viewModel.setFilterQuery(contextualSearchQuery)
        }
        sortController?.setQuery(contextualSearchQuery)
    }

    private fun showGroupSearch() {
        val input = android.widget.EditText(requireContext()).apply {
            setSingleLine(true)
            hint = getString(R.string.subscription_groups_search)
            setText(contextualSearchQuery)
            setSelection(text.length)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.subscription_groups_search)
            .setView(input)
            .setPositiveButton(R.string.search) { _, _ ->
                if (feedGroupsCollapsed && input.text?.isNotBlank() == true) {
                    toggleGroupsCollapsed()
                }
                setContextualSearchQuery(input.text?.toString().orEmpty())
            }
            .setNeutralButton(R.string.subscription_groups_clear_search) { _, _ ->
                setContextualSearchQuery("")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleListViewMode() {
        viewModel.setListViewMode(!viewModel.getListViewMode())
    }

    private fun toggleGroupsCollapsed() {
        feedGroupsCollapsed = !feedGroupsCollapsed
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
            .putBoolean(SubscriptionGroupsCollapse.key(activeProfileId), feedGroupsCollapsed)
            .apply()
        feedGroupsSortMenuItem.collapsed = feedGroupsCollapsed
        feedGroupsSortMenuItem.notifyChanged(GroupsHeader.PAYLOAD_UPDATE_ICONS)
        feedGroupsSection.update(
            if (feedGroupsCollapsed) emptyList() else listOf(feedGroupsCarousel)
        )
    }

    override fun doInitialLoadLogic() = Unit
    override fun startLoading(forceLoad: Boolean) = Unit

    private val listenerChannelItem = object : OnClickGesture<ChannelInfoItem> {
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(
            fm,
            selectedItem.serviceId,
            selectedItem.url,
            selectedItem.name
        )

        override fun held(selectedItem: ChannelInfoItem) = channelActions.show(selectedItem)
    }

    override fun handleResult(result: SubscriptionState) {
        super.handleResult(result)

        when (result) {
            is SubscriptionState.LoadedState -> {
                result.subscriptions.forEach {
                    if (it is org.schabi.newpipe.local.subscription.item.SubscriptionSeparatorItem) {
                        val entry = it.entry
                        it.onEdit = { sortController?.editSeparator(entry) }
                    }
                    if (it is ChannelItem) {
                        it.gesturesListener = listenerChannelItem
                        it.itemVersion = if (SubscriptionViewModel.shouldUseGridForSubscription(requireContext())) {
                            ChannelItem.ItemVersion.GRID
                        } else {
                            ChannelItem.ItemVersion.MINI
                        }
                    }
                }

                val anchor = if (sortingEnabled) null else browseScrollAnchor()
                subscriptionsSection.update(result.subscriptions)
                subscriptionsSection.setHideWhenEmpty(false)

                if (itemsListState != null) {
                    binding.itemsList.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
                } else {
                    restoreBrowseScroll(anchor)
                }
            }

            is SubscriptionState.ErrorState -> {
                result.error?.let {
                    showError(ErrorInfo(result.error, UserAction.SOMETHING_ELSE, "Subscriptions"))
                }
            }
        }
    }

    private fun handleFeedGroups(groups: List<Group>, listViewMode: Boolean) {
        if (feedGroupsCarouselState != null) {
            feedGroupsCarousel.onRestoreInstanceState(feedGroupsCarouselState)
            feedGroupsCarouselState = null
        }

        binding.itemsList.post {
            if (context == null) {
                // since this part was posted to the next UI cycle, the fragment might have been
                // removed in the meantime
                return@post
            }

            feedGroupsCarousel.listViewMode = listViewMode
            feedGroupsSortMenuItem.showSortButton = groups.size > 1
            feedGroupsSortMenuItem.listViewMode = listViewMode
            feedGroupsCarousel.notifyChanged(FeedGroupCarouselItem.PAYLOAD_UPDATE_LIST_VIEW_MODE)
            feedGroupsSortMenuItem.notifyChanged(GroupsHeader.PAYLOAD_UPDATE_ICONS)

            // update items here to prevent flickering
            carouselAdapter.apply {
                clear()
                if (listViewMode) {
                    add(FeedGroupAddNewItem())
                    add(FeedGroupImportItem())
                    add(FeedGroupCardItem(GROUP_ALL_ID, getString(R.string.all), FeedGroupIcon.WHATS_NEW))
                } else {
                    add(FeedGroupAddNewGridItem())
                    add(FeedGroupImportGridItem())
                    add(FeedGroupCardGridItem(GROUP_ALL_ID, getString(R.string.all), FeedGroupIcon.WHATS_NEW))
                }
                addAll(groups)
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Contract
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        binding.itemsList.animate(false, 100)
    }

    override fun hideLoading() {
        super.hideLoading()
        if (sortingEnabled) updateSortingVisibility() else binding.itemsList.animate(true, 200)
    }

    companion object {
        val JSON_MIME_TYPE = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension("json") ?: "application/octet-stream"
    }
}
