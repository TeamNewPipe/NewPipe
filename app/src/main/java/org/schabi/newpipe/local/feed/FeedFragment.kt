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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evernote.android.state.State
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.OnItemLongClickListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.ktx.slideUp
import org.schabi.newpipe.local.feed.item.StreamItem
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper.getGridSpanCountStreams
import org.schabi.newpipe.util.ThemeHelper.getItemViewMode
import org.schabi.newpipe.util.ThemeHelper.resolveDrawable
import org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout
import java.time.OffsetDateTime
import java.util.function.Consumer

class FeedFragment : BaseStateFragment<FeedState>() {
    private var _feedBinding: FragmentFeedBinding? = null
    private val feedBinding get() = _feedBinding!!

    private val disposables = CompositeDisposable()

    private lateinit var viewModel: FeedViewModel
    @State @JvmField var listState: Parcelable? = null

    private var groupId = FeedGroupEntity.GROUP_ALL_ID
    private var groupName = ""
    private var oldestSubscriptionUpdate: OffsetDateTime? = null

    private lateinit var groupAdapter: GroupieAdapter

    private var onSettingsChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var updateListViewModeOnResume = false
    private var isRefreshing = false

    private var lastNewItemsCount = 0

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = arguments?.getLong(KEY_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
            ?: FeedGroupEntity.GROUP_ALL_ID
        groupName = arguments?.getString(KEY_GROUP_NAME) ?: ""

        onSettingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (getString(R.string.list_view_mode_key).equals(key)) {
                updateListViewModeOnResume = true
            }
        }
        PreferenceManager.getDefaultSharedPreferences(activity)
            .registerOnSharedPreferenceChangeListener(onSettingsChangeListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        // super.onViewCreated() calls initListeners() which require the binding to be initialized
        _feedBinding = FragmentFeedBinding.bind(rootView)
        super.onViewCreated(rootView, savedInstanceState)

        val factory = FeedViewModel.getFactory(requireContext(), groupId)
        viewModel = ViewModelProvider(this, factory)[FeedViewModel::class.java]
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(::handleResult) }

        groupAdapter = GroupieAdapter().apply {
            setOnItemClickListener(listenerStreamItem)
            setOnItemLongClickListener(listenerStreamItem)
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
        setupListViewMode()
    }

    override fun onPause() {
        super.onPause()
        listState = feedBinding.itemsList.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        updateRelativeTimeViews()

        if (updateListViewModeOnResume) {
            updateListViewModeOnResume = false

            setupListViewMode()
            if (viewModel.stateLiveData.value != null) {
                handleResult(viewModel.stateLiveData.value!!)
            }
        }
    }

    private fun setupListViewMode() {
        // does everything needed to setup the layouts for grid or list modes
        groupAdapter.spanCount = if (shouldUseGridLayout(context)) getGridSpanCountStreams(context) else 1
        feedBinding.itemsList.layoutManager = GridLayoutManager(requireContext(), groupAdapter.spanCount).apply {
            spanSizeLookup = groupAdapter.spanSizeLookup
        }
    }

    override fun initListeners() {
        super.initListeners()
        feedBinding.refreshRootView.setOnClickListener { reloadContent() }
        feedBinding.swipeRefreshLayout.setOnRefreshListener { reloadContent() }
        feedBinding.newItemsLoadedButton.setOnClickListener {
            hideNewItemsLoaded(true)
            feedBinding.itemsList.scrollToPosition(0)
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Menu
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.fragment_feed_title)
        activity.supportActionBar?.subtitle = groupName

        inflater.inflate(R.menu.menu_feed_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_feed_help) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            val usingDedicatedMethod = sharedPreferences
                .getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
            val enableDisableButtonText = when {
                usingDedicatedMethod -> R.string.feed_use_dedicated_fetch_method_disable_button
                else -> R.string.feed_use_dedicated_fetch_method_enable_button
            }

            AlertDialog.Builder(requireContext())
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

    private fun showStreamVisibilityDialog() {
        val dialogItems = arrayOf(
            getString(R.string.feed_show_watched),
            getString(R.string.feed_show_partially_watched),
            getString(R.string.feed_show_upcoming)
        )

        val checkedDialogItems = booleanArrayOf(
            viewModel.getShowPlayedItemsFromPreferences(),
            viewModel.getShowPartiallyPlayedItemsFromPreferences(),
            viewModel.getShowFutureItemsFromPreferences()
        )

        AlertDialog.Builder(context!!)
            .setTitle(R.string.feed_hide_streams_title)
            .setMultiChoiceItems(dialogItems, checkedDialogItems) { _, which, isChecked ->
                checkedDialogItems[which] = isChecked
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.setSaveShowPlayedItems(checkedDialogItems[0])
                viewModel.setSaveShowPartiallyPlayedItems(checkedDialogItems[1])
                viewModel.setSaveShowFutureItems(checkedDialogItems[2])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyOptionsMenu() {
        super.onDestroyOptionsMenu()
        activity?.supportActionBar?.subtitle = null
    }

    override fun onDestroy() {
        disposables.dispose()
        if (onSettingsChangeListener != null) {
            PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(onSettingsChangeListener)
            onSettingsChangeListener = null
        }

        super.onDestroy()
        activity?.supportActionBar?.subtitle = null
    }

    override fun onDestroyView() {
        // Ensure that all animations are canceled
        tryGetNewItemsLoadedButton()?.clearAnimation()

        feedBinding.itemsList.adapter = null
        _feedBinding = null
        super.onDestroyView()
    }

    // //////////////////////////////////////////////////////////////////////////
    // Handling
    // //////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        feedBinding.itemsList.animateHideRecyclerViewAllowingScrolling()
        feedBinding.refreshRootView.animate(false, 0)
        feedBinding.loadingProgressText.animate(true, 200)
        feedBinding.swipeRefreshLayout.isRefreshing = true
        isRefreshing = true
    }

    override fun hideLoading() {
        super.hideLoading()
        feedBinding.itemsList.animate(true, 0)
        feedBinding.refreshRootView.animate(true, 200)
        feedBinding.loadingProgressText.animate(false, 0)
        feedBinding.swipeRefreshLayout.isRefreshing = false
        isRefreshing = false
    }

    override fun showEmptyState() {
        super.showEmptyState()
        feedBinding.itemsList.animateHideRecyclerViewAllowingScrolling()
        feedBinding.refreshRootView.animate(true, 200)
        feedBinding.loadingProgressText.animate(false, 0)
        feedBinding.swipeRefreshLayout.isRefreshing = false
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
        super.handleError()
        feedBinding.itemsList.animateHideRecyclerViewAllowingScrolling()
        feedBinding.refreshRootView.animate(false, 0)
        feedBinding.loadingProgressText.animate(false, 0)
        feedBinding.swipeRefreshLayout.isRefreshing = false
        isRefreshing = false
    }

    private fun handleProgressState(progressState: FeedState.ProgressState) {
        showLoading()

        val isIndeterminate = progressState.currentProgress == -1 &&
            progressState.maxProgress == -1

        feedBinding.loadingProgressText.text = if (!isIndeterminate) {
            "${progressState.currentProgress}/${progressState.maxProgress}"
        } else if (progressState.progressMessage > 0) {
            getString(progressState.progressMessage)
        } else {
            "∞/∞"
        }

        feedBinding.loadingProgressBar.isIndeterminate = isIndeterminate ||
            (progressState.maxProgress > 0 && progressState.currentProgress == 0)
        feedBinding.loadingProgressBar.progress = progressState.currentProgress

        feedBinding.loadingProgressBar.max = progressState.maxProgress
    }

    private fun showInfoItemDialog(item: StreamInfoItem) {
        val context = context
        val activity: Activity? = getActivity()
        if (context == null || context.resources == null || activity == null) return

        InfoItemDialog.Builder(activity, context, this, item).create().show()
    }

    private val listenerStreamItem = object : OnItemClickListener, OnItemLongClickListener {
        override fun onItemClick(item: Item<*>, view: View) {
            if (item is StreamItem && !isRefreshing) {
                val stream = item.streamWithState.stream
                NavigationHelper.openVideoDetailFragment(
                    requireContext(), fm,
                    stream.serviceId, stream.url, stream.title, null, false
                )
            }
        }

        override fun onItemLongClick(item: Item<*>, view: View): Boolean {
            if (item is StreamItem && !isRefreshing) {
                showInfoItemDialog(item.streamWithState.stream.toStreamInfoItem())
                return true
            }
            return false
        }
    }

    @SuppressLint("StringFormatMatches")
    private fun handleLoadedState(loadedState: FeedState.LoadedState) {
        val itemVersion = when (getItemViewMode(requireContext())) {
            ItemViewMode.GRID -> StreamItem.ItemVersion.GRID
            ItemViewMode.CARD -> StreamItem.ItemVersion.CARD
            else -> StreamItem.ItemVersion.NORMAL
        }
        loadedState.items.forEach { it.itemVersion = itemVersion }

        // This need to be saved in a variable as the update occurs async
        val oldOldestSubscriptionUpdate = oldestSubscriptionUpdate

        groupAdapter.updateAsync(loadedState.items, false) {
            oldOldestSubscriptionUpdate?.run {
                highlightNewItemsAfter(oldOldestSubscriptionUpdate)
            }
        }

        listState?.run {
            feedBinding.itemsList.layoutManager?.onRestoreInstanceState(listState)
            listState = null
        }

        val feedsNotLoaded = loadedState.notLoadedCount > 0
        feedBinding.refreshSubtitleText.isVisible = feedsNotLoaded
        if (feedsNotLoaded) {
            feedBinding.refreshSubtitleText.text = getString(
                R.string.feed_subscription_not_loaded_count,
                loadedState.notLoadedCount
            )
        }

        if (oldestSubscriptionUpdate != loadedState.oldestUpdate ||
            (oldestSubscriptionUpdate == null && loadedState.oldestUpdate == null)
        ) {
            // ignore errors if they have already been handled for the current update
            handleItemsErrors(loadedState.itemsErrors)
        }
        oldestSubscriptionUpdate = loadedState.oldestUpdate

        if (loadedState.items.isEmpty()) {
            showEmptyState()
        } else {
            hideLoading()
        }
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
            if (t is FeedLoadService.RequestException &&
                t.cause is ContentNotAvailableException
            ) {
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
            getString(R.string.feed_use_dedicated_fetch_method_key), false
        )

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.feed_load_error)
            .setPositiveButton(R.string.unsubscribe) { _, _ ->
                SubscriptionManager(requireContext())
                    .deleteSubscription(subscriptionEntity.serviceId, subscriptionEntity.url)
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
        }
        builder.setMessage(message)
            .show()
    }

    private fun updateRelativeTimeViews() {
        updateRefreshViewState()
        groupAdapter.notifyItemRangeChanged(
            0, groupAdapter.itemCount,
            StreamItem.UPDATE_RELATIVE_TIME
        )
    }

    private fun updateRefreshViewState() {
        feedBinding.refreshText.text = getString(
            R.string.feed_oldest_subscription_update,
            oldestSubscriptionUpdate?.let { Localization.relativeTime(it) } ?: "—"
        )
    }

    /**
     * Highlights all items that are after the specified time
     */
    private fun highlightNewItemsAfter(updateTime: OffsetDateTime) {
        var highlightCount = 0

        var doCheck = true

        for (i in 0 until groupAdapter.itemCount) {
            val item = groupAdapter.getItem(i) as StreamItem

            var typeface = Typeface.DEFAULT
            var backgroundSupplier = { ctx: Context ->
                resolveDrawable(ctx, android.R.attr.selectableItemBackground)
            }
            if (doCheck) {
                // If the uploadDate is null or true we should highlight the item
                if (item.streamWithState.stream.uploadDate?.isAfter(updateTime) != false) {
                    highlightCount++

                    typeface = Typeface.DEFAULT_BOLD
                    backgroundSupplier = { ctx: Context ->
                        // Merge the drawables together. Otherwise we would lose the "select" effect
                        LayerDrawable(
                            arrayOf(
                                resolveDrawable(ctx, R.attr.dashed_border),
                                resolveDrawable(ctx, android.R.attr.selectableItemBackground)
                            )
                        )
                    }
                } else {
                    // Decreases execution time due to the order of the items (newest always on top)
                    // Once a item is is before the updateTime we can skip all following items
                    doCheck = false
                }
            }

            // The highlighter has to be always set
            // When it's only set on items that are highlighted it will highlight all items
            // due to the fact that itemRoot is getting recycled
            item.execBindEnd = Consumer { viewBinding ->
                val context = viewBinding.itemRoot.context
                viewBinding.itemRoot.background = backgroundSupplier.invoke(context)
                viewBinding.itemVideoTitleView.typeface = typeface
            }
        }

        // Force updates all items so that the highlighting is correct
        // If this isn't done visible items that are already highlighted will stay in a highlighted
        // state until the user scrolls them out of the visible area which causes a update/bind-call
        groupAdapter.notifyItemRangeChanged(
            0,
            highlightCount.coerceIn(lastNewItemsCount, groupAdapter.itemCount)
        )

        if (highlightCount > 0) {
            showNewItemsLoaded()
        }

        lastNewItemsCount = highlightCount
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

    override fun reloadContent() {
        hideNewItemsLoaded(false)

        getActivity()?.startService(
            Intent(requireContext(), FeedLoadService::class.java).apply {
                putExtra(FeedLoadService.EXTRA_GROUP_ID, groupId)
            }
        )
        listState = null
    }

    companion object {
        const val KEY_GROUP_ID = "ARG_GROUP_ID"
        const val KEY_GROUP_NAME = "ARG_GROUP_NAME"

        @JvmStatic
        fun newInstance(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, groupName: String? = null): FeedFragment {
            val feedFragment = FeedFragment()
            feedFragment.arguments = bundleOf(KEY_GROUP_ID to groupId, KEY_GROUP_NAME to groupName)
            return feedFragment
        }
    }
}
