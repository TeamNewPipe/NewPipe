/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import io.github.yewplay.core.designsystem.YpSubscribeLabels
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.search.SearchActions
import io.github.yewplay.feature.search.SearchIcons
import io.github.yewplay.feature.search.SearchScreen
import io.github.yewplay.feature.search.SearchStrings
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.util.KEY_SEARCH_STRING
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: the Search destination (proposal, p. 13), drawn in Compose by :feature:search.
 * It has its own field with Cancel, so the activity hides its toolbar while it is shown.
 */
class YpSearchFragment : BaseFragment(), BackPressable {
    private lateinit var viewModel: YpSearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpSearchViewModel::class.java]
        val args = arguments ?: Bundle()
        val query = savedInstanceState?.getString(STATE_QUERY)
            ?: args.getString(KEY_SEARCH_STRING).orEmpty()
        viewModel.init(
            args.getInt(KEY_SERVICE_ID),
            query,
            savedInstanceState?.getString(STATE_FILTER)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val themeKey = YpThemePrefs.themeKey(context)
        val nightThemeKey = YpThemePrefs.nightThemeKey(context)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SearchEvent.OpenLink -> {
                                fm.popBackStackImmediate()
                                requireActivity().startActivity(event.intent)
                            }
                            is SearchEvent.ShowError ->
                                ErrorUtil.showSnackbar(this@YpSearchFragment, event.errorInfo)
                        }
                    }
                }
                YpTheme(themeKey, nightThemeKey) {
                    SearchScreen(
                        state = state,
                        strings = SearchStrings(
                            cancel = stringResource(R.string.cancel),
                            clear = stringResource(R.string.clear),
                            retry = stringResource(R.string.retry),
                            openInBrowser = stringResource(R.string.open_in_browser),
                            noResults = stringResource(R.string.search_no_results),
                            noResultsDetail = stringResource(R.string.empty_list_subtitle),
                            subscribe = YpSubscribeLabels(
                                subscribe = stringResource(R.string.subscribe_button_title),
                                subscribed = stringResource(R.string.subscribed_button_title)
                            ),
                            didYouMean = stringResource(R.string.did_you_mean),
                            showingResultsFor = stringResource(R.string.search_showing_result_for)
                        ),
                        icons = SearchIcons(
                            search = painterResource(R.drawable.ic_search),
                            clear = painterResource(R.drawable.ic_close),
                            history = painterResource(R.drawable.ic_history),
                            insert = painterResource(R.drawable.ic_search_add)
                        ),
                        actions = actions()
                    )
                }
            }
        }
    }

    private fun actions() = SearchActions(
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::search,
        onCancel = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        onClear = viewModel::onClear,
        onFieldFocused = viewModel::onFieldFocused,
        onSuggestionClick = { viewModel.search(it.query) },
        onSuggestionInsert = { viewModel.editQuery(it.query) },
        onSuggestionLongClick = { if (it.fromHistory) confirmDeleteFromHistory(it.query) },
        onFilterSelect = viewModel::onFilterSelect,
        onItemClick = ::openItem,
        onItemLongClick = ::showItemMenu,
        onToggleSubscription = viewModel::toggleSubscription,
        onLoadMore = viewModel::loadMore,
        onCorrectionClick = { viewModel.state.value.correction?.let(viewModel::search) },
        onCorrectionLongClick = { viewModel.state.value.correction?.let(viewModel::editQuery) },
        onNoticeClick = { notice ->
            notice.url?.let { ShareUtils.openUrlInBrowser(requireContext(), it) }
        },
        onRetry = viewModel::retry,
        onErrorAction = ::onErrorAction,
        onOpenInBrowser = {
            viewModel.lastError?.openInBrowserUrl?.let {
                ShareUtils.openUrlInBrowser(requireContext(), it)
            }
        }
    )

    private fun openItem(item: YpListItem) {
        try {
            when (item) {
                is YpStreamItem -> NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    fm,
                    item.serviceId,
                    item.url,
                    item.title,
                    null,
                    false
                )
                is YpChannelItem ->
                    NavigationHelper.openChannelFragment(fm, item.serviceId, item.url, item.name)
                is YpPlaylistItem ->
                    NavigationHelper.openPlaylistFragment(fm, item.serviceId, item.url, item.name)
            }
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening search result", e)
        }
    }

    private fun showItemMenu(item: YpListItem) {
        val stream = viewModel.streamItemFor(item) ?: return
        try {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, stream)
                .create()
                .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, stream)
        }
    }

    private fun confirmDeleteFromHistory(query: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(query)
            .setMessage(R.string.delete_item_search_history)
            .setCancelable(true)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteFromHistory(query) }
            .show()
    }

    private fun onErrorAction() {
        val error = viewModel.lastError ?: return
        val recaptchaUrl = error.recaptchaUrl
        if (recaptchaUrl != null) {
            val intent = Intent(requireContext(), ReCaptchaActivity::class.java)
            intent.putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, recaptchaUrl)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
        } else {
            ErrorUtil.openActivity(requireContext(), error)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST && resultCode == Activity.RESULT_OK) {
            viewModel.retry()
        }
    }

    override fun onBackPressed(): Boolean = viewModel.onBackPressed()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
            STATE_QUERY,
            viewModel.searchString.ifEmpty { viewModel.state.value.query }
        )
        outState.putString(STATE_FILTER, viewModel.contentFilter.firstOrNull())
    }

    companion object {
        private const val STATE_QUERY = "yp_search_query"
        private const val STATE_FILTER = "yp_search_filter"

        @JvmStatic
        fun newInstance(serviceId: Int, searchString: String?): YpSearchFragment =
            YpSearchFragment().apply {
                arguments = Bundle().apply {
                    putInt(KEY_SERVICE_ID, serviceId)
                    putString(KEY_SEARCH_STRING, searchString.orEmpty())
                }
            }
    }
}
