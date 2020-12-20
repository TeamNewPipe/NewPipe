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

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.util.Localization
import java.time.OffsetDateTime

class FeedFragment : BaseListFragment<FeedState, Unit>() {
    private var _feedBinding: FragmentFeedBinding? = null
    private val feedBinding get() = _feedBinding!!

    private lateinit var viewModel: FeedViewModel
    @State
    @JvmField
    var listState: Parcelable? = null

    private var groupId = FeedGroupEntity.GROUP_ALL_ID
    private var groupName = ""
    private var oldestSubscriptionUpdate: OffsetDateTime? = null

    init {
        setHasOptionsMenu(true)
        setUseDefaultStateSaving(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = arguments?.getLong(KEY_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
            ?: FeedGroupEntity.GROUP_ALL_ID
        groupName = arguments?.getString(KEY_GROUP_NAME) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        // super.onViewCreated() calls initListeners() which require the binding to be initialized
        _feedBinding = FragmentFeedBinding.bind(rootView)
        super.onViewCreated(rootView, savedInstanceState)

        viewModel = ViewModelProvider(this, FeedViewModel.Factory(requireContext(), groupId)).get(FeedViewModel::class.java)
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(::handleResult) }
    }

    override fun onPause() {
        super.onPause()
        listState = feedBinding.itemsList.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        updateRelativeTimeViews()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)

        if (!isVisibleToUser && view != null) {
            updateRelativeTimeViews()
        }
    }

    override fun initListeners() {
        super.initListeners()
        feedBinding.refreshRootView.setOnClickListener { reloadContent() }
        feedBinding.swipeRefreshLayout.setOnRefreshListener { reloadContent() }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Menu
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        activity.supportActionBar?.setTitle(R.string.fragment_feed_title)
        activity.supportActionBar?.subtitle = groupName

        inflater.inflate(R.menu.menu_feed_fragment, menu)

        if (useAsFrontPage) {
            menu.findItem(R.id.menu_item_feed_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_feed_help) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            val usingDedicatedMethod = sharedPreferences.getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
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
                .setPositiveButton(resources.getString(R.string.finish), null)
                .create()
                .show()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyOptionsMenu() {
        super.onDestroyOptionsMenu()
        activity?.supportActionBar?.subtitle = null
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.supportActionBar?.subtitle = null
    }

    override fun onDestroyView() {
        _feedBinding = null
        super.onDestroyView()
    }

    // /////////////////////////////////////////////////////////////////////////
    // Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        feedBinding.itemsList.animateHideRecyclerViewAllowingScrolling()
        feedBinding.refreshRootView.animate(false, 0)
        feedBinding.loadingProgressText.animate(true, 200)
        feedBinding.swipeRefreshLayout.isRefreshing = true
    }

    override fun hideLoading() {
        super.hideLoading()
        feedBinding.refreshRootView.animate(true, 200)
        feedBinding.loadingProgressText.animate(false, 0)
        feedBinding.swipeRefreshLayout.isRefreshing = false
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
        infoListAdapter.clearStreamItemList()
        feedBinding.itemsList.animateHideRecyclerViewAllowingScrolling()
        feedBinding.refreshRootView.animate(false, 0)
        feedBinding.loadingProgressText.animate(false, 0)
        feedBinding.swipeRefreshLayout.isRefreshing = false
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

    private fun handleLoadedState(loadedState: FeedState.LoadedState) {
        infoListAdapter.setInfoItemList(loadedState.items)
        listState?.run {
            feedBinding.itemsList.layoutManager?.onRestoreInstanceState(listState)
            listState = null
        }

        oldestSubscriptionUpdate = loadedState.oldestUpdate

        val loadedCount = loadedState.notLoadedCount > 0
        feedBinding.refreshSubtitleText.isVisible = loadedCount
        if (loadedCount) {
            feedBinding.refreshSubtitleText.text = getString(
                R.string.feed_subscription_not_loaded_count,
                loadedState.notLoadedCount
            )
        }

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

    private fun updateRelativeTimeViews() {
        updateRefreshViewState()
        infoListAdapter.notifyDataSetChanged()
    }

    private fun updateRefreshViewState() {
        feedBinding.refreshText.text = getString(
            R.string.feed_oldest_subscription_update,
            oldestSubscriptionUpdate?.let { Localization.relativeTime(it) } ?: "—"
        )
    }

    // /////////////////////////////////////////////////////////////////////////
    // Load Service Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun doInitialLoadLogic() {}
    override fun loadMoreItems() {}
    override fun hasMoreItems() = false

    override fun reloadContent() {
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
