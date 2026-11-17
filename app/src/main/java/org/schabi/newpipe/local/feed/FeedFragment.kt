/*
 * Copyright 2019 Mauricio Colli <mauriciocolli@outlook.com>
 * FeedFragment.kt is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.local.feed

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evernote.android.state.State
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.OnAsyncUpdateListener
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.OnItemLongClickListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.info_list.StreamSelectionController
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.ktx.slideUp
import org.schabi.newpipe.learning.VideoNoteBadges
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.search.ContextualSearchable
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.recommendations.LocalRecommendations
import org.schabi.newpipe.themes.GlassFeedLayout
import org.schabi.newpipe.util.ContentBlockingHelper
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.MembersOnlyContentHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StreamFilterControls
import org.schabi.newpipe.util.StreamFilterSelection
import org.schabi.newpipe.util.StreamListFilter
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode
import org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout

class FeedFragment : BaseStateFragment<FeedState>(), ContextualSearchable {
    private var _feedBinding: FragmentFeedBinding? = null
    private val feedBinding get() = _feedBinding!!

    private val disposables = CompositeDisposable()

    private lateinit var viewModel: FeedViewModel

    @State
    @JvmField
    var listState: Parcelable? = null

    private var groupId = FeedGroupEntity.GROUP_ALL_ID
    private var groupName = ""
    private var groupSelector: FeedGroupSelector? = null
    private var oldestSubscriptionUpdate: OffsetDateTime? = null
    private var latestLoadedState: FeedState.LoadedState? = null
    private var contextualSearchQuery = ""

    @State
    @JvmField
    var selectedStreamFilters = ""

    @State
    @JvmField
    var feedHeaderExpanded = true

    private var noteBadges: VideoNoteBadges? = null
    private var feedQuickAdd: org.schabi.newpipe.local.feed.quickadd.FeedQuickAddController? = null
    private var feedPreview: org.schabi.newpipe.local.feed.preview.FeedPreviewController? = null
    private lateinit var groupAdapter: GroupieAdapter
    private var streamSelection: StreamSelectionController? = null

    private lateinit var onSettingsChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private var updateListViewModeOnResume = false
    private var updateFeedItems = true

    private var feedHasContent: Boolean? = null
    private var idleAnimator: RecyclerView.ItemAnimator? = null
    private var pendingListUpdate: OnAsyncUpdateListener? = null
    private var relevanceSortDisposable: Disposable? = null
    private var relevanceSortRequest = 0L

    private var glassLayout: GlassFeedLayout? = null
    private var glassBottomInset = 0

    private val feedHeaderOffsetListener = AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
        feedHeaderExpanded = verticalOffset == 0
    }

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = arguments?.getLong(KEY_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
            ?: FeedGroupEntity.GROUP_ALL_ID
        groupName = arguments?.getString(KEY_GROUP_NAME) ?: ""
        val storedFilter = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(streamFilterPreferenceKey(groupId), StreamListFilter.NONE.name)
        selectedStreamFilters = StreamFilterSelection.parse(storedFilter).encode()

        onSettingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == getString(R.string.feed_floating_refresh_key)) {
                updateRefreshControls()
            } else if (org.schabi.newpipe.util.ThemeHelper.isListLayoutPreference(requireContext(), key)) {
                updateListViewModeOnResume = true
            } else if (ContentBlockingHelper.isPreferenceKey(requireContext(), key)) {
                latestLoadedState?.let { showFilteredFeedItems(it, false) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        // super.onViewCreated() calls initListeners() which require the binding to be initialized
        _feedBinding = FragmentFeedBinding.bind(rootView)
        super.onViewCreated(rootView, savedInstanceState)
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(onSettingsChangeListener)

        val factory = FeedViewModel.getFactory(requireContext(), groupId)
        viewModel = ViewModelProvider(this, factory)[FeedViewModel::class.java]
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(::handleResult) }

        groupAdapter = GroupieAdapter().apply {
            setOnItemClickListener(listenerStreamItem)
            setOnItemLongClickListener(listenerStreamItem)
        }
        streamSelection = StreamSelectionController(
            this,
            feedBinding.itemsList,
            {
                (0 until groupAdapter.itemCount).mapNotNull { position ->
                    (groupAdapter.getItem(position) as? StreamItem)
                        ?.streamWithState?.stream?.toStreamInfoItem()
                }
            },
            { position ->
                if (position in 0 until groupAdapter.itemCount) {
                    (groupAdapter.getItem(position) as? StreamItem)
                        ?.streamWithState?.stream?.toStreamInfoItem()
                } else {
                    null
                }
            }
        )

        val restoreFeedHeaderExpanded = feedHeaderExpanded
        feedBinding.feedHeader.addOnOffsetChangedListener(feedHeaderOffsetListener)
        feedBinding.feedHeader.post {
            _feedBinding?.feedHeader?.setExpanded(restoreFeedHeaderExpanded, false)
        }

        feedBinding.itemsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // Check if we scrolled to the top
                if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                    !recyclerView.canScrollVertically(-1)
                ) {
                    if (tryGetNewItemsLoadedButton()?.isVisible == true) {
                        hideNewItemsLoaded(true)
                    }
                }
            }
        })

        feedBinding.itemsList.adapter = groupAdapter
        idleAnimator = feedBinding.itemsList.itemAnimator
        feedQuickAdd = org.schabi.newpipe.local.feed.quickadd.FeedQuickAddController(feedBinding.itemsList)
        feedPreview = org.schabi.newpipe.local.feed.preview.FeedPreviewController(feedBinding.itemsList, { stream, queue ->
            NavigationHelper.openVideoDetailFragment(requireContext(), fm, stream.serviceId, stream.url, stream.title, queue, false)
        })
        noteBadges = VideoNoteBadges(requireContext(), Runnable { groupAdapter.notifyDataSetChanged() })
        noteBadges?.watch(feedBinding.itemsList)
        setupListViewMode()
        activity.supportActionBar?.let {
            groupSelector = FeedGroupSelector(requireContext(), it, { groupId }, ::selectFeedGroup)
        }
    }

    override fun onPause() {
        glassLayout?.detach()
        glassLayout = null
        feedPreview?.stop()
        super.onPause()
        listState = feedBinding.itemsList.layoutManager?.onSaveInstanceState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null) {
            GridLayoutManagerHelper.preserveScrollPosition(feedBinding.itemsList) {
                GridLayoutManagerHelper.reinflateViewHolders(feedBinding.itemsList)
                setupListViewMode()
                viewModel.stateLiveData.value?.let { handleResult(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val host = activity as? MainActivity
        if (parentFragment is MainFragment && host != null) {
            glassLayout = GlassFeedLayout.attach(feedBinding, host) { inset ->
                glassBottomInset = inset
                updateRefreshControls()
            }
        }
        updateRelativeTimeViews()

        if (updateListViewModeOnResume) {
            updateListViewModeOnResume = false

            setupListViewMode()
            if (viewModel.stateLiveData.value != null) {
                handleResult(viewModel.stateLiveData.value!!)
            }
        }
    }

    private fun selectFeedGroup(id: Long, name: String) {
        val changed = groupId != id
        groupId = id
        groupName = name
        // Preserve the selection across view and process recreation without adding screens.
        if (arguments == null) arguments = Bundle()
        requireArguments().putLong(KEY_GROUP_ID, id)
        requireArguments().putString(KEY_GROUP_NAME, name)
        activity.invalidateOptionsMenu()
        if (!changed) return
        feedPreview?.stop()
        listState = null
        latestLoadedState = null
        oldestSubscriptionUpdate = null
        feedHasContent = null
        pendingListUpdate = null
        hideNewItemsLoaded(false)
        groupAdapter.clear()
        feedBinding.itemsList.scrollToPosition(0)
        selectedStreamFilters = StreamFilterSelection.parse(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(streamFilterPreferenceKey(id), "")
        ).encode()
        bindStreamFilters()
        viewModel.selectGroup(id)
    }

    private fun setupListViewMode() {
        updateFeedItems = true
        // does everything needed to setup the layouts for grid or list modes
        val gridMode = shouldUseGridLayout(context)
        val minimumItemWidth = resources.getDimensionPixelSize(
            R.dimen.video_item_grid_thumbnail_image_width
        ) + resources.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2
        feedBinding.itemsList.layoutManager = if (gridMode) {
            GridLayoutManagerHelper.create(
                feedBinding.itemsList,
                minimumItemWidth,
                GridLayoutManagerHelper.getPreferredSpanCount(requireContext())
            ) { spanCount ->
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

    override fun initListeners() {
        super.initListeners()
        feedBinding.refreshRootView.setOnClickListener { reloadContent() }
        updateRefreshControls()
        feedBinding.refreshIcon.setOnClickListener { reloadContent() }
        feedBinding.feedFloatingRefresh.setOnClickListener {
            if (feedBinding.refreshProgressContainer.isVisible) {
                feedBinding.cancelRefreshButton.performClick()
            } else {
                reloadContent()
            }
        }
        feedBinding.refreshSubtitleText.setOnClickListener { showNotLoadedSubscriptions() }
        feedBinding.swipeRefreshLayout.setOnRefreshListener { reloadContent() }
        feedBinding.cancelRefreshButton.setOnClickListener {
            it.isEnabled = false
            feedBinding.feedFloatingRefresh.isEnabled = false
            FeedLoadService.cancel(requireContext())
        }
        feedBinding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            !feedHeaderExpanded || feedBinding.itemsList.canScrollVertically(-1)
        }
        feedBinding.newItemsLoadedButton.setOnClickListener {
            scrollToTop()
        }
        bindStreamFilters()
    }

    private fun bindStreamFilters() {
        StreamFilterControls.bind(feedBinding.streamFilterChips, selectedStreamFilters, true) { selection ->
            selectedStreamFilters = selection
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putString(streamFilterPreferenceKey(groupId), selectedStreamFilters)
            }
            latestLoadedState?.let { showFilteredFeedItems(it, false) }
        }
        feedBinding.streamFilterChips.filterAll.setText(R.string.filter_all_visible)
        feedBinding.streamFilterChips.filterWatchAll.setText(R.string.filter_all_visible)
    }

    fun scrollToTop() {
        val binding = _feedBinding ?: return
        if (tryGetNewItemsLoadedButton()?.isVisible == true) {
            hideNewItemsLoaded(true)
        }
        binding.itemsList.stopScroll()
        binding.itemsList.scrollToPosition(0)
        binding.feedHeader.setExpanded(true, true)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Menu
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.fragment_feed_title)
        activity.supportActionBar?.subtitle = null
        groupSelector?.attach(activity.findViewById<View>(R.id.toolbar_contextual_search_container)?.isVisible == true)

        inflater.inflate(R.menu.menu_feed_fragment, menu)
        menu.findItem(R.id.menu_item_feed_channels)?.isVisible =
            groupId != FeedGroupEntity.GROUP_ALL_ID
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_feed_refresh_all) {
            refreshFeed(ignoreOutdatedThreshold = true)
            return true
        } else if (item.itemId == R.id.menu_item_feed_channels) {
            when (GroupChannelsNavigationHost.forOpenedFeedGroup()) {
                GroupChannelsNavigationHost.ACTIVITY -> {
                    NavigationHelper.openGroupChannelsFragment(
                        requireActivity().supportFragmentManager,
                        groupId,
                        groupName
                    )
                }
            }
            return true
        } else if (item.itemId == R.id.menu_item_feed_sort) {
            val modes = buildList {
                add(FeedSortMode.PUBLICATION)
                add(FeedSortMode.DISCOVERY)
                if (LocalRecommendations.enabled(requireContext())) {
                    add(FeedSortMode.RELEVANCE)
                }
            }
            val labels = modes.map { mode ->
                getString(
                    when (mode) {
                        FeedSortMode.PUBLICATION -> R.string.feed_sort_publication
                        FeedSortMode.DISCOVERY -> R.string.feed_sort_discovery
                        FeedSortMode.RELEVANCE -> R.string.feed_sort_relevance
                    }
                )
            }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feed_sort_title)
                .setSingleChoiceItems(
                    labels,
                    modes.indexOf(viewModel.getSortMode()).coerceAtLeast(0)
                ) { dialog, which ->
                    viewModel.setSortMode(modes[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return true
        } else if (item.itemId == R.id.menu_item_feed_help) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            val usingDedicatedMethod = sharedPreferences
                .getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
            val enableDisableButtonText = when {
                usingDedicatedMethod -> R.string.feed_use_dedicated_fetch_method_disable_button
                else -> R.string.feed_use_dedicated_fetch_method_enable_button
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feed_use_dedicated_fetch_method_title)
                .setMessage(R.string.feed_use_dedicated_fetch_method_help_text)
                .setNeutralButton(enableDisableButtonText) { _, _ ->
                    sharedPreferences.edit {
                        putBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), !usingDedicatedMethod)
                    }
                }
                .setPositiveButton(resources.getString(R.string.ok), null)
                .show()
            return true
        } else if (item.itemId == R.id.menu_item_feed_toggle_played_items) {
            showStreamVisibilityDialog()
        }

        return super.onOptionsItemSelected(item)
    }

    private data class VisibilityDialogItem(val title: Int, val key: Int, val checked: Boolean)

    private fun showStreamVisibilityDialog() {
        val context = requireContext()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        fun preferenceItem(title: Int, key: Int, default: Boolean = true) = VisibilityDialogItem(title, key, preferences.getBoolean(getString(key), default))

        val dialogItems = buildList {
            add(
                VisibilityDialogItem(
                    R.string.feed_show_watched,
                    R.string.feed_show_watched_items_key,
                    viewModel.getShowPlayedItemsFromPreferences()
                )
            )
            add(
                VisibilityDialogItem(
                    R.string.feed_show_partially_watched,
                    R.string.feed_show_partially_watched_items_key,
                    viewModel.getShowPartiallyPlayedItemsFromPreferences()
                )
            )
            add(
                VisibilityDialogItem(
                    R.string.feed_show_upcoming,
                    R.string.feed_show_future_items_key,
                    viewModel.getShowFutureItemsFromPreferences()
                )
            )
            add(preferenceItem(R.string.channel_tab_livestreams, R.string.feed_show_live_items_key))
            add(preferenceItem(R.string.videos_string, R.string.feed_show_video_items_key))
            add(preferenceItem(R.string.channel_tab_shorts, R.string.feed_show_shorts_items_key))
            if (MembersOnlyContentHelper.shouldHide(context)) {
                add(preferenceItem(R.string.members_only, R.string.feed_show_members_only_items_key, DEFAULT_SHOW_MEMBERS_ONLY_IN_FEED))
            }
        }
        val checkedItems = dialogItems.map { it.checked }.toBooleanArray()
        val dateKey = getString(R.string.feed_date_filter_epoch_day_key)
        var selectedDate = readFeedCutoffDate(preferences)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val dateButton = Button(context)
        val clearButton = Button(context).apply { setText(R.string.feed_date_filter_clear) }

        fun updateDateControls() {
            dateButton.text = selectedDate?.let { getString(R.string.feed_date_filter_selected, it.format(formatter)) }
                ?: getString(R.string.feed_date_filter)
            clearButton.isVisible = selectedDate != null
        }

        dateButton.setOnClickListener {
            val start = selectedDate ?: LocalDate.now()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    updateDateControls()
                },
                start.year,
                start.monthValue - 1,
                start.dayOfMonth
            ).show()
        }
        clearButton.setOnClickListener {
            selectedDate = null
            updateDateControls()
        }
        updateDateControls()
        val dateControls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPaddingRelative(padding, 0, padding, 0)
            addView(dateButton)
            addView(clearButton)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_default_visibility_title)
            .setMultiChoiceItems(dialogItems.map { getString(it.title) }.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setView(dateControls)
            .setPositiveButton(R.string.ok) { _, _ ->
                preferences.edit {
                    dialogItems.forEachIndexed { index, item ->
                        putBoolean(getString(item.key), checkedItems[index])
                    }
                    val date = selectedDate
                    if (date == null) remove(dateKey) else putLong(dateKey, date.toEpochDay())
                }
                latestLoadedState?.let { showFilteredFeedItems(it, false) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun readFeedCutoffDate(preferences: SharedPreferences): LocalDate? {
        val key = getString(R.string.feed_date_filter_epoch_day_key)
        return if (preferences.contains(key)) LocalDate.ofEpochDay(preferences.getLong(key, 0L)) else null
    }

    override fun onDestroyOptionsMenu() {
        groupSelector?.detach()
        super.onDestroyOptionsMenu()
        if (
            (groupName != "") &&
            (activity?.supportActionBar?.subtitle == groupName)
        ) {
            activity?.supportActionBar?.subtitle = null
        }
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()

        if (
            (groupName != "") &&
            (activity?.supportActionBar?.subtitle == groupName)
        ) {
            activity?.supportActionBar?.subtitle = null
        }
    }

    override fun onDestroyView() {
        feedQuickAdd?.dispose()
        feedQuickAdd = null
        feedPreview?.dispose()
        feedPreview = null
        groupSelector?.dispose()
        groupSelector = null
        streamSelection?.destroy()
        streamSelection = null
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(onSettingsChangeListener)

        // Ensure that all animations are canceled
        tryGetNewItemsLoadedButton()?.clearAnimation()

        feedBinding.feedHeader.removeOnOffsetChangedListener(feedHeaderOffsetListener)
        noteBadges?.unwatch(feedBinding.itemsList)
        noteBadges = null
        pendingListUpdate = null
        relevanceSortRequest++
        relevanceSortDisposable?.dispose()
        relevanceSortDisposable = null
        feedHasContent = null
        idleAnimator = null
        feedBinding.itemsList.adapter = null
        latestLoadedState = null
        _feedBinding = null
        super.onDestroyView()
    }

    // //////////////////////////////////////////////////////////////////////////
    // Handling
    // //////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        val binding = _feedBinding ?: return
        feedHasContent = null
        showRefreshProgress(binding)
        super.showLoading()
        binding.itemsList.animate(true, 0)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun hideLoading() {
        val binding = _feedBinding ?: return
        feedHasContent = true
        super.hideLoading()
        binding.itemsList.animate(true, 0)
        binding.refreshRootView.animate(true, 200)
        if (latestLoadedState?.progress == null) hideRefreshProgress(binding)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun showEmptyState() {
        val binding = _feedBinding ?: return
        feedHasContent = false
        super.showEmptyState()
        binding.itemsList.animateHideRecyclerViewAllowingScrolling()
        binding.refreshRootView.animate(true, 200)
        if (latestLoadedState?.progress == null) hideRefreshProgress(binding)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun handleResult(result: FeedState) {
        when (result) {
            is FeedState.ProgressState -> handleProgressState(result)
            is FeedState.LoadedState -> handleLoadedState(result)
            is FeedState.ErrorState -> if (handleErrorState(result)) return
        }

        updateRefreshViewState()
    }

    override fun handleError() {
        val binding = _feedBinding ?: return
        feedHasContent = null
        pendingListUpdate = null
        updateFeedItems = true
        super.handleError()
        binding.itemsList.animateHideRecyclerViewAllowingScrolling()
        binding.refreshRootView.animate(false, 0)
        hideRefreshProgress(binding)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun handleProgressState(progressState: FeedState.ProgressState) {
        // Content and refresh progress coexist, including after recreating the view.
        if (!feedBinding.refreshProgressContainer.isVisible) showRefreshProgress(feedBinding)
        feedBinding.swipeRefreshLayout.isRefreshing = false

        val isIndeterminate = progressState.currentProgress < 0 ||
            progressState.maxProgress < 0
        feedBinding.feedRefreshProgressBar.isVisible = !isIndeterminate
        feedBinding.feedRefreshIndeterminateProgressBar.isVisible = isIndeterminate
        feedBinding.loadingChannelText.text = progressState.updateDescription
        feedBinding.loadingChannelText.isVisible = progressState.updateDescription.isNotBlank()
        feedBinding.loadingProgressText.isVisible = !isIndeterminate

        if (isIndeterminate) {
            feedBinding.feedRefreshIndeterminateProgressBar.contentDescription =
                if (progressState.progressMessage > 0) {
                    getString(progressState.progressMessage)
                } else {
                    getString(R.string.feed_notification_loading)
                }
        } else {
            val maxProgress = progressState.maxProgress.coerceAtLeast(1)
            val currentProgress = progressState.currentProgress.coerceIn(0, maxProgress)
            feedBinding.loadingProgressText.text = "$currentProgress / $maxProgress"
            feedBinding.feedRefreshProgressBar.max = maxProgress
            feedBinding.feedRefreshProgressBar.setProgressCompat(currentProgress, true)
            feedBinding.feedRefreshProgressBar.contentDescription =
                getString(R.string.feed_notification_loading) + " " +
                currentProgress + "/" + maxProgress
        }
    }

    private fun showRefreshProgress(binding: FragmentFeedBinding) {
        binding.itemsList.itemAnimator = null
        hideNewItemsLoaded(false)
        binding.feedFloatingRefresh.setImageResource(R.drawable.ic_close)
        binding.feedFloatingRefresh.contentDescription = getString(R.string.cancel_refresh)
        binding.streamFilterChips.root.isVisible = false
        binding.refreshProgressContainer.isVisible = true
        binding.cancelRefreshButton.isVisible = true
        binding.refreshRootView.isEnabled = false
        binding.cancelRefreshButton.isEnabled = true
        updateRefreshControls()
    }

    private fun hideRefreshProgress(binding: FragmentFeedBinding) {
        if (!binding.refreshProgressContainer.isVisible) return
        binding.itemsList.itemAnimator = idleAnimator
        binding.feedFloatingRefresh.isEnabled = true
        binding.feedFloatingRefresh.setImageResource(R.drawable.ic_refresh)
        binding.feedFloatingRefresh.contentDescription = getString(R.string.local_media_refresh)
        binding.cancelRefreshButton.isEnabled = false
        binding.refreshRootView.isEnabled = true
        binding.refreshProgressContainer.isVisible = false
        binding.cancelRefreshButton.isVisible = false
        binding.streamFilterChips.root.isVisible = true
        updateRefreshControls()
    }

    private fun updateRefreshControls() {
        val binding = _feedBinding ?: return
        val floating = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.feed_floating_refresh_key), false)
        val refreshing = binding.refreshProgressContainer.isVisible
        binding.feedFloatingRefresh.isVisible = floating
        binding.swipeRefreshLayout.isEnabled = !floating
        if (floating) binding.swipeRefreshLayout.isRefreshing = false
        binding.refreshIcon.isVisible = !floating
        binding.refreshRootView.isClickable = !floating && !refreshing
        binding.refreshRootView.isFocusable = !floating
        binding.cancelRefreshButton.isVisible = refreshing && !floating
        binding.itemsList.setPadding(
            binding.itemsList.paddingLeft,
            binding.itemsList.paddingTop,
            binding.itemsList.paddingRight,
            glassBottomInset + if (floating) resources.getDimensionPixelSize(R.dimen.feed_floating_refresh_bottom_padding) else 0
        )
    }

    private fun showInfoItemDialog(item: StreamInfoItem) {
        val context = context
        val activity: Activity? = getActivity()
        if (context == null || context.resources == null || activity == null) return

        InfoItemDialog.Builder(activity, context, this, item)
            .addEntry(
                StreamDialogEntry(R.string.stream_select) { _, selected ->
                    streamSelection?.start(selected)
                }
            ).create().show()
    }

    private val listenerStreamItem = object : OnItemClickListener, OnItemLongClickListener {
        override fun onItemClick(item: Item<*>, view: View) {
            if (item is StreamItem) {
                val stream = item.streamWithState.stream
                if (streamSelection?.toggleIfActive(stream.toStreamInfoItem()) == true) {
                    return
                }
                if (stream.requiresMembership) {
                    MembersOnlyContentHelper.showExplanation(requireContext())
                    return
                }
                if (
                    ServiceHelper.isYoutubeMusicMode(requireContext()) &&
                    stream.serviceId == SubscriptionEntity.YOUTUBE_SERVICE_ID
                ) {
                    feedPreview?.stop()
                    NavigationHelper.playOnBackgroundPlayer(
                        requireContext(),
                        SinglePlayQueue(stream.toStreamInfoItem()),
                        true
                    )
                    return
                }
                NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    fm,
                    stream.serviceId,
                    stream.url,
                    stream.title,
                    feedPreview?.takeQueue(stream),
                    false
                )
            }
        }

        override fun onItemLongClick(item: Item<*>, view: View): Boolean {
            if (item is StreamItem) {
                if (streamSelection?.toggleIfActive(
                        item.streamWithState.stream.toStreamInfoItem()
                    ) == true
                ) {
                    return true
                }
                showInfoItemDialog(item.streamWithState.stream.toStreamInfoItem())
                return true
            }
            return false
        }
    }

    private fun openUploaderChannel(channel: ChannelInfoItem) {
        try {
            NavigationHelper.openChannelFragment(
                fm,
                channel.serviceId,
                channel.url,
                channel.name
            )
        } catch (error: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", error)
        }
    }

    @SuppressLint("StringFormatMatches")
    private fun handleLoadedState(loadedState: FeedState.LoadedState) {
        val previousState = latestLoadedState
        latestLoadedState = loadedState
        if (loadedState.progress != null) {
            handleProgressState(loadedState.progress)
        } else {
            hideRefreshProgress(feedBinding)
        }
        if (updateFeedItems || previousState?.items !== loadedState.items ||
            previousState?.highlightAfter != loadedState.highlightAfter ||
            previousState?.refreshCompletion !== loadedState.refreshCompletion
        ) {
            updateFeedItems = false
            showFilteredFeedItems(loadedState, true)
        }

        val feedsNotLoaded = loadedState.notLoadedCount > 0
        feedBinding.refreshSubtitleText.isVisible = feedsNotLoaded
        if (feedsNotLoaded) {
            feedBinding.refreshSubtitleText.text = getString(
                R.string.feed_subscription_not_loaded_count_details,
                loadedState.notLoadedCount
            )
            feedBinding.refreshSubtitleText.isClickable = true
            feedBinding.refreshSubtitleText.isFocusable = true
        } else {
            feedBinding.refreshSubtitleText.isClickable = false
            feedBinding.refreshSubtitleText.isFocusable = false
        }

        if (loadedState.itemsErrors.isNotEmpty() && previousState?.itemsErrors != loadedState.itemsErrors) {
            // ignore errors if they have already been handled for the current update
            handleItemsErrors(loadedState.itemsErrors)
        }
        oldestSubscriptionUpdate = loadedState.oldestUpdate
    }

    private fun showNotLoadedSubscriptions() {
        val loadedState = latestLoadedState ?: return
        val failedIds = FeedFailureDetails.subscriptionIds(loadedState.itemsErrors)
        if (failedIds.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feed_not_loaded_details_title)
                .setMessage(R.string.feed_not_loaded_details_unavailable)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        disposables.add(
            Single.fromCallable {
                val dao = NewPipeDatabase.getInstance(requireContext()).subscriptionDAO()
                failedIds.mapNotNull { id ->
                    runCatching { dao.getSubscription(id) }.getOrNull()
                }
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { subscriptions ->
                        if (!isAdded || _feedBinding == null) {
                            return@subscribe
                        }
                        showFailedSubscriptionsDialog(subscriptions)
                    },
                    { error ->
                        Log.e(TAG, "Unable to load failed subscriptions", error)
                        ErrorUtil.showUiErrorSnackbar(
                            this,
                            "Loading failed subscription details",
                            error
                        )
                    }
                )
        )
    }

    private fun showFailedSubscriptionsDialog(subscriptions: List<SubscriptionEntity>) {
        if (subscriptions.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feed_not_loaded_details_title)
                .setMessage(R.string.feed_not_loaded_details_unavailable)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val ordered = subscriptions.sortedBy { it.name.orEmpty().lowercase() }
        val labels = ordered.map(::failedSubscriptionLabel).toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.feed_not_loaded_details_title)
            .setItems(labels) { dialog, which ->
                val subscription = ordered[which]
                dialog.dismiss()
                try {
                    NavigationHelper.openChannelFragment(
                        fm,
                        subscription.serviceId,
                        subscription.url.orEmpty(),
                        failedSubscriptionLabel(subscription)
                    )
                } catch (error: Exception) {
                    ErrorUtil.showUiErrorSnackbar(
                        this,
                        "Opening failed subscription",
                        error
                    )
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun failedSubscriptionLabel(subscription: SubscriptionEntity): String {
        return subscription.name
            ?.takeIf { it.isNotBlank() }
            ?: subscription.url.orEmpty()
    }

    private fun showFilteredFeedItems(
        loadedState: FeedState.LoadedState,
        restoreListState: Boolean,
        relevanceOrder: Map<String, Int>? = null
    ) {
        if (_feedBinding == null) {
            return
        }

        val itemVersion = when (getItemViewMode(requireContext())) {
            ItemViewMode.GRID -> StreamItem.ItemVersion.GRID
            ItemViewMode.CARD -> StreamItem.ItemVersion.CARD
            else -> StreamItem.ItemVersion.NORMAL
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val hideMembersOnly = shouldHideMembersOnlyInFeed(
            MembersOnlyContentHelper.shouldHide(requireContext()),
            preferences.getBoolean(
                getString(R.string.feed_show_members_only_items_key),
                DEFAULT_SHOW_MEMBERS_ONLY_IN_FEED
            )
        )
        val blockingRules = ContentBlockingHelper.getRules(
            requireContext(),
            ContentBlockingHelper.Target.SUBSCRIPTIONS
        )
        val thresholdTime = readFeedCutoffDate(preferences)
            ?.atStartOfDay(ZoneId.systemDefault())
            ?.toOffsetDateTime()
        val shownStreamCategories = buildSet {
            if (preferences.getBoolean(getString(R.string.feed_show_live_items_key), true)) {
                add(StreamListFilter.LIVE)
            }
            if (preferences.getBoolean(getString(R.string.feed_show_video_items_key), true)) {
                add(StreamListFilter.VIDEOS)
            }
            if (preferences.getBoolean(getString(R.string.feed_show_shorts_items_key), true)) {
                add(StreamListFilter.SHORTS)
            }
            if (viewModel.getShowFutureItemsFromPreferences()) {
                add(StreamListFilter.UPCOMING)
            }
        }
        val selection = StreamFilterSelection.parse(selectedStreamFilters)
        val showWatched = viewModel.getShowPlayedItemsFromPreferences()
        val showPartiallyWatched = viewModel.getShowPartiallyPlayedItemsFromPreferences()
        val streamFilteredItems = loadedState.items.filter { item ->
            val streamWithState = item.streamWithState
            if (hideMembersOnly && streamWithState.stream.requiresMembership) {
                return@filter false
            }
            val stream = streamWithState.stream.toStreamInfoItem()
            if (thresholdTime != null && stream.uploadDate?.offsetDateTime()?.isBefore(thresholdTime) == true) {
                return@filter false
            }
            if (blockingRules.isBlocked(stream)) {
                return@filter false
            }
            val state = streamWithState.stateProgressMillis?.let {
                StreamStateEntity(streamWithState.stream.uid, it)
            }
            selection.matchesVisible(
                stream,
                state,
                shownStreamCategories,
                showWatched,
                showPartiallyWatched
            )
        }
        val searchedItems = ContextualSearchHelper.filter(
            streamFilteredItems,
            contextualSearchQuery
        ) { item ->
            val stream = item.streamWithState.stream
            arrayOf(stream.title, stream.uploader)
        }
        val relevanceEnabled = viewModel.getSortMode() == FeedSortMode.RELEVANCE
        if (relevanceOrder == null) {
            val request = ++relevanceSortRequest
            relevanceSortDisposable?.dispose()
            relevanceSortDisposable = null
            if (relevanceEnabled) {
                val applicationContext = requireContext().applicationContext
                val rankingItems = searchedItems.map {
                    it.streamWithState.stream.toStreamInfoItem()
                }
                relevanceSortDisposable = Single.fromCallable {
                    LocalRecommendations.relevanceOrder(applicationContext, rankingItems)
                }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { order ->
                            relevanceSortDisposable = null
                            if (request == relevanceSortRequest && _feedBinding != null) {
                                showFilteredFeedItems(loadedState, restoreListState, order)
                            }
                        },
                        { error ->
                            Log.e(TAG, "Unable to rank the feed by relevance", error)
                            relevanceSortDisposable = null
                            if (request == relevanceSortRequest && _feedBinding != null) {
                                showFilteredFeedItems(
                                    loadedState,
                                    restoreListState,
                                    emptyMap()
                                )
                            }
                        }
                    )
                return
            }
        }
        val effectiveRelevanceOrder = if (relevanceEnabled) {
            relevanceOrder.orEmpty()
        } else {
            emptyMap()
        }
        val orderedItems = if (effectiveRelevanceOrder.isEmpty()) {
            searchedItems
        } else {
            searchedItems.sortedBy { item ->
                val stream = item.streamWithState.stream
                effectiveRelevanceOrder["${stream.serviceId}:${stream.url}"] ?: Int.MAX_VALUE
            }
        }
        val displayedItems = orderedItems.map { item ->
            val highlighted = loadedState.highlightAfter?.let {
                shouldHighlightFeedStream(item.streamWithState.stream, it)
            } == true
            item.copy(itemVersion = itemVersion, isHighlighted = highlighted).also { displayItem ->
                displayItem.noteBadges = noteBadges
                displayItem.previewController = feedPreview?.let { WeakReference(it) }
                displayItem.quickAddController = feedQuickAdd?.let { WeakReference(it) }
                displayItem.onUploaderSelected = ::openUploaderChannel
            }
        }

        val completion = loadedState.refreshCompletion
        // Groupie keeps the listener weakly. Keep it alive only for the latest submission.
        val listener = object : OnAsyncUpdateListener {
            override fun onUpdateComplete() {
                if (pendingListUpdate !== this) return
                pendingListUpdate = null
                streamSelection?.refresh()
                if (latestLoadedState?.refreshCompletion === completion &&
                    completion?.announceIfNew(displayedItems) == true
                ) {
                    showNewItemsLoaded()
                }
            }
        }
        pendingListUpdate = listener
        groupAdapter.updateAsync(displayedItems, false, listener)

        if (restoreListState) {
            listState?.run {
                feedBinding.itemsList.layoutManager?.onRestoreInstanceState(listState)
                listState = null
            }
        }

        setEmptyStateMessage(
            if (ContextualSearchHelper.isActive(contextualSearchQuery) ||
                selectedStreamFilters.isNotEmpty()
            ) {
                R.string.search_no_results
            } else {
                R.string.empty_list_subtitle
            }
        )
        if (feedHasContent != displayedItems.isNotEmpty()) {
            if (displayedItems.isEmpty()) showEmptyState() else hideLoading()
        }
    }

    override fun setContextualSearchQuery(query: String) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query)
        latestLoadedState?.let { showFilteredFeedItems(it, false) }
    }

    private fun handleErrorState(errorState: FeedState.ErrorState): Boolean {
        return if (errorState.error == null) {
            hideLoading()
            false
        } else {
            showError(ErrorInfo(errorState.error, UserAction.REQUESTED_FEED, "Loading feed"))
            true
        }
    }

    private fun handleItemsErrors(errors: List<Throwable>) {
        errors.forEachIndexed { i, t ->
            val isUnavailableFeed = t.cause is ContentNotAvailableException ||
                t.cause is ContentNotSupportedException
            if (t is FeedLoadService.RequestException && isUnavailableFeed) {
                disposables.add(
                    Single.fromCallable {
                        NewPipeDatabase.getInstance(requireContext()).subscriptionDAO()
                            .getSubscription(t.subscriptionId)
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { subscriptionEntity ->
                                handleFeedNotAvailable(
                                    subscriptionEntity,
                                    t.cause,
                                    errors.subList(i + 1, errors.size)
                                )
                            },
                            { throwable -> Log.e(TAG, "Unable to process", throwable) }
                        )
                )
                // this will be called on the remaining errors by handleFeedNotAvailable()
                return@handleItemsErrors
            }
        }

        if (errors.isNotEmpty()) {
            // if no error was a ContentNotAvailableException, show a general error snackbar
            ErrorUtil.showSnackbar(this, ErrorInfo(errors, UserAction.REQUESTED_FEED, ""))
        }
    }

    private fun handleFeedNotAvailable(
        subscriptionEntity: SubscriptionEntity,
        cause: Throwable?,
        nextItemsErrors: List<Throwable>
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isFastFeedModeEnabled = sharedPreferences.getBoolean(
            getString(R.string.feed_use_dedicated_fetch_method_key),
            false
        )

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feed_load_error)
            .setPositiveButton(R.string.unsubscribe) { _, _ ->
                SubscriptionManager(requireContext())
                    .deleteSubscription(subscriptionEntity.serviceId, subscriptionEntity.url!!)
                    .subscribe()
                handleItemsErrors(nextItemsErrors)
            }
            .setNegativeButton(R.string.cancel, null)

        var message = getString(R.string.feed_load_error_account_info, subscriptionEntity.name)
        if (cause is AccountTerminatedException) {
            message += "\n" + getString(R.string.feed_load_error_terminated)
        } else if (cause is ContentNotAvailableException) {
            if (isFastFeedModeEnabled) {
                message += "\n" + getString(R.string.feed_load_error_fast_unknown)
                builder.setNeutralButton(R.string.feed_use_dedicated_fetch_method_disable_button) { _, _ ->
                    sharedPreferences.edit {
                        putBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
                    }
                }
            } else if (!isNullOrEmpty(cause.message)) {
                message += "\n" + cause.message
            }
        } else if (!isNullOrEmpty(cause?.message)) {
            message += "\n" + cause?.message
        }
        builder.setMessage(message)
            .show()
    }

    private fun updateRelativeTimeViews() {
        updateRefreshViewState()
        groupAdapter.notifyItemRangeChanged(
            0,
            groupAdapter.itemCount,
            StreamItem.UPDATE_RELATIVE_TIME
        )
    }

    private fun updateRefreshViewState() {
        feedBinding.refreshText.text = getString(
            R.string.feed_oldest_subscription_update,
            oldestSubscriptionUpdate?.let { Localization.relativeTime(it) } ?: "—"
        )
    }

    private fun showNewItemsLoaded() {
        tryGetNewItemsLoadedButton()?.clearAnimation()
        tryGetNewItemsLoadedButton()
            ?.slideUp(
                250L,
                delay = 100,
                execOnEnd = {
                    // Disabled animations would result in immediately hiding the button
                    // after it showed up
                    // Context can be null in some cases, so we have to make sure it is not null in
                    // order to avoid a NullPointerException
                    context?.let {
                        if (DeviceUtils.hasAnimationsAnimatorDurationEnabled(it)) {
                            // Hide the new items button after 10s
                            hideNewItemsLoaded(true, 10000)
                        }
                    }
                }
            )
    }

    private fun hideNewItemsLoaded(animate: Boolean, delay: Long = 0) {
        tryGetNewItemsLoadedButton()?.clearAnimation()
        if (animate) {
            tryGetNewItemsLoadedButton()?.animate(
                false,
                200,
                delay = delay,
                execOnEnd = {
                    // Make the layout invisible so that the onScroll toTop method
                    // only does necessary work
                    tryGetNewItemsLoadedButton()?.isVisible = false
                }
            )
        } else {
            tryGetNewItemsLoadedButton()?.isVisible = false
        }
    }

    /**
     * The view/button can be disposed/set to null under certain circumstances.
     * E.g. when the animation is still in progress but the view got destroyed.
     * This method is a helper for such states and can be used in affected code blocks.
     */
    private fun tryGetNewItemsLoadedButton(): Button? {
        return _feedBinding?.newItemsLoadedButton
    }

    // /////////////////////////////////////////////////////////////////////////
    // Load Service Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun doInitialLoadLogic() {}

    override fun reloadContent() = refreshFeed(ignoreOutdatedThreshold = false)

    private fun refreshFeed(ignoreOutdatedThreshold: Boolean) {
        hideNewItemsLoaded(false)
        getActivity()?.startService(
            FeedLoadService.createLoadIntent(
                requireContext(),
                groupId,
                FeedScope.forFeeds(requireContext()),
                ignoreOutdatedThreshold
            )
        )
        listState = null
    }

    companion object {
        const val KEY_GROUP_ID = "ARG_GROUP_ID"
        const val KEY_GROUP_NAME = "ARG_GROUP_NAME"
        internal const val DEFAULT_SHOW_MEMBERS_ONLY_IN_FEED = true
        private const val STREAM_FILTER_PREF_PREFIX = "feed_stream_filter_"

        internal fun streamFilterPreferenceKey(groupId: Long): String = "$STREAM_FILTER_PREF_PREFIX$groupId"

        internal fun restoreStreamFilter(storedValue: String?): StreamListFilter = storedValue?.let { value ->
            runCatching { StreamListFilter.valueOf(value) }.getOrDefault(StreamListFilter.NONE)
        } ?: StreamListFilter.NONE

        internal fun shouldHideMembersOnlyInFeed(
            hideMembersOnlyGlobally: Boolean,
            showMembersOnlyInFeed: Boolean
        ): Boolean = hideMembersOnlyGlobally || !showMembersOnlyInFeed

        @JvmStatic
        fun newInstance(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, groupName: String? = null): FeedFragment {
            val feedFragment = FeedFragment()
            feedFragment.arguments = bundleOf(KEY_GROUP_ID to groupId, KEY_GROUP_NAME to groupName)
            return feedFragment
        }
    }
}
