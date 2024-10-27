package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.evernote.android.state.State
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.GroupieViewHolder
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_ALL_ID
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCarouselItem
import org.schabi.newpipe.local.subscription.item.GroupsHeader
import org.schabi.newpipe.local.subscription.item.Header
import org.schabi.newpipe.local.subscription.item.ImportSubscriptionsHintPlaceholderItem
import org.schabi.newpipe.local.subscription.services.SubscriptionsExportService
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_MODE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_VALUE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.PREVIOUS_EXPORT_MODE
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper.getGridSpanCountChannels
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionFragment : BaseStateFragment<SubscriptionState>() {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var subscriptionManager: SubscriptionManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val groupAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var carouselAdapter: GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>
    private lateinit var feedGroupsCarousel: FeedGroupCarouselItem
    private lateinit var feedGroupsSortMenuItem: GroupsHeader
    private val subscriptionsSection = Section()

    private val requestExportLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestExportResult)
    private val requestImportLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestImportResult)

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
        subscriptionManager = SubscriptionManager(requireContext())
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
        super.onDestroyView()
        _binding = null
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

        buildImportExportMenu(menu)
    }

    private fun buildImportExportMenu(menu: Menu) {
        // -- Import --
        val importSubMenu = menu.addSubMenu(R.string.import_from)

        addMenuItemToSubmenu(importSubMenu, R.string.previous_export) { onImportPreviousSelected() }
            .setIcon(R.drawable.ic_backup)

        for (service in ServiceList.all()) {
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

        addMenuItemToSubmenu(exportSubMenu, R.string.file) { onExportSelected() }
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

    private fun onImportPreviousSelected() {
        NoFileManagerSafeGuard.launchSafe(
            requestImportLauncher,
            StoredFileHelper.getPicker(activity, JSON_MIME_TYPE),
            TAG,
            requireContext()
        )
    }

    private fun onExportSelected() {
        val date = SimpleDateFormat("yyyyMMddHHmm", Locale.ENGLISH).format(Date())
        val exportName = "newpipe_subscriptions_$date.json"

        NoFileManagerSafeGuard.launchSafe(
            requestExportLauncher,
            StoredFileHelper.getNewPicker(activity, exportName, JSON_MIME_TYPE, null),
            TAG,
            requireContext()
        )
    }

    private fun openReorderDialog() {
        FeedGroupReorderDialog().show(parentFragmentManager, null)
    }

    private fun requestExportResult(result: ActivityResult) {
        if (result.data != null && result.resultCode == Activity.RESULT_OK) {
            activity.startService(
                Intent(activity, SubscriptionsExportService::class.java)
                    .putExtra(SubscriptionsExportService.KEY_FILE_PATH, result.data?.data)
            )
        }
    }

    private fun requestImportResult(result: ActivityResult) {
        if (result.data != null && result.resultCode == Activity.RESULT_OK) {
            ImportConfirmationDialog.show(
                this,
                Intent(activity, SubscriptionsImportService::class.java)
                    .putExtra(KEY_MODE, PREVIOUS_EXPORT_MODE)
                    .putExtra(KEY_VALUE, result.data?.data)
            )
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        _binding = FragmentSubscriptionBinding.bind(rootView)

        groupAdapter.spanCount = if (SubscriptionViewModel.shouldUseGridForSubscription(requireContext())) getGridSpanCountChannels(context) else 1
        binding.itemsList.layoutManager = GridLayoutManager(requireContext(), groupAdapter.spanCount).apply {
            spanSizeLookup = groupAdapter.spanSizeLookup
        }
        binding.itemsList.adapter = groupAdapter
        binding.itemsList.itemAnimator = null

        viewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(this::handleResult) }
        viewModel.feedGroupsLiveData.observe(viewLifecycleOwner) {
            it?.let { (groups, listViewMode) ->
                handleFeedGroups(groups, listViewMode)
            }
        }

        setupInitialLayout()
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
                        FeedGroupDialog.newInstance(item.groupId).show(fm, null)
                    is FeedGroupCardGridItem ->
                        FeedGroupDialog.newInstance(item.groupId).show(fm, null)
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
                onToggleListViewModeClicked = ::toggleListViewMode,
                listViewMode = viewModel.getListViewMode(),
            )

            add(Section(feedGroupsSortMenuItem, listOf(feedGroupsCarousel)))
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

    private fun toggleListViewMode() {
        viewModel.setListViewMode(!viewModel.getListViewMode())
    }

    private fun showLongTapDialog(selectedItem: ChannelInfoItem) {
        val commands = arrayOf(
            getString(R.string.share),
            getString(R.string.open_in_browser),
            getString(R.string.unsubscribe)
        )

        val actions = DialogInterface.OnClickListener { _, i ->
            when (i) {
                0 -> ShareUtils.shareText(
                    requireContext(), selectedItem.name, selectedItem.url, selectedItem.thumbnails
                )
                1 -> ShareUtils.openUrlInBrowser(requireContext(), selectedItem.url)
                2 -> deleteChannel(selectedItem)
            }
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(requireContext()))
        dialogTitleBinding.root.isSelected = true
        dialogTitleBinding.itemTitleView.text = selectedItem.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands, actions)
            .show()
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteSubscription(selectedItem.serviceId, selectedItem.url).subscribe {
                Toast.makeText(requireContext(), getString(R.string.channel_unsubscribed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun doInitialLoadLogic() = Unit
    override fun startLoading(forceLoad: Boolean) = Unit

    private val listenerChannelItem = object : OnClickGesture<ChannelInfoItem> {
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(
            fm,
            selectedItem.serviceId, selectedItem.url, selectedItem.name
        )

        override fun held(selectedItem: ChannelInfoItem) = showLongTapDialog(selectedItem)
    }

    override fun handleResult(result: SubscriptionState) {
        super.handleResult(result)

        when (result) {
            is SubscriptionState.LoadedState -> {
                result.subscriptions.forEach {
                    if (it is ChannelItem) {
                        it.gesturesListener = listenerChannelItem
                        it.itemVersion = if (SubscriptionViewModel.shouldUseGridForSubscription(requireContext())) {
                            ChannelItem.ItemVersion.GRID
                        } else {
                            ChannelItem.ItemVersion.MINI
                        }
                    }
                }

                subscriptionsSection.update(result.subscriptions)
                subscriptionsSection.setHideWhenEmpty(false)

                if (itemsListState != null) {
                    binding.itemsList.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
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
                    add(FeedGroupCardItem(GROUP_ALL_ID, getString(R.string.all), FeedGroupIcon.WHATS_NEW))
                } else {
                    add(FeedGroupAddNewGridItem())
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
        binding.itemsList.animate(true, 200)
    }

    companion object {
        const val JSON_MIME_TYPE = "application/json"
    }
}
