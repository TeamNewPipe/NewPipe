/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import io.github.yewplay.core.designsystem.YpErrorLabels
import io.github.yewplay.core.designsystem.YpPlaylistControlIcons
import io.github.yewplay.core.designsystem.YpPlaylistControlLabels
import io.github.yewplay.core.designsystem.YpSubscribeLabels
import io.github.yewplay.core.designsystem.YpTheme
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.channel.ChannelActions
import io.github.yewplay.feature.channel.ChannelIcons
import io.github.yewplay.feature.channel.ChannelScreen
import io.github.yewplay.feature.channel.ChannelStrings
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.yewplay.YpThemePrefs
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * YewPlay: a channel (proposal, p. 13), drawn in Compose by :feature:channel. It has its own
 * top bar over the banner, so the activity hides its toolbar while it is shown.
 */
class YpChannelFragment : BaseFragment() {
    private lateinit var viewModel: YpChannelViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[YpChannelViewModel::class.java]
        val args = arguments ?: Bundle()
        viewModel.init(
            serviceId = args.getInt(KEY_SERVICE_ID),
            url = args.getString(KEY_URL).orEmpty(),
            name = args.getString(KEY_TITLE).orEmpty(),
            frontPage = useAsFrontPage || savedInstanceState?.getBoolean(STATE_FRONT_PAGE) == true,
            selectedTab = savedInstanceState?.getInt(STATE_TAB) ?: 0
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
                            is ChannelEvent.ShowError ->
                                ErrorUtil.showSnackbar(this@YpChannelFragment, event.errorInfo)

                            ChannelEvent.Subscribed -> showNotifySnackbar()
                        }
                    }
                }
                YpTheme(themeKey, nightThemeKey) {
                    ChannelScreen(
                        state = state,
                        strings = ChannelStrings(
                            subscribe = YpSubscribeLabels(
                                subscribe = stringResource(R.string.subscribe_button_title),
                                subscribed = stringResource(R.string.subscribed_button_title)
                            ),
                            notifications = stringResource(R.string.get_notified),
                            rss = stringResource(R.string.rss_button_title),
                            share = stringResource(R.string.share),
                            openInBrowser = stringResource(R.string.open_in_browser),
                            settings = stringResource(R.string.settings),
                            back = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            search = stringResource(R.string.search),
                            error = YpErrorLabels(
                                retry = stringResource(R.string.retry),
                                openInBrowser = stringResource(R.string.open_in_browser)
                            ),
                            noVideos = stringResource(R.string.no_videos),
                            emptyDetail = stringResource(R.string.empty_list_subtitle),
                            contentNotSupported = stringResource(R.string.content_not_supported),
                            tags = stringResource(R.string.metadata_tags),
                            controls = YpPlaylistControlLabels(
                                play = stringResource(R.string.yp_play),
                                shuffle = stringResource(R.string.yp_shuffle),
                                listen = stringResource(R.string.controls_background_title)
                            )
                        ),
                        icons = ChannelIcons(
                            back = painterResource(R.drawable.ic_arrow_back),
                            search = painterResource(R.drawable.ic_search),
                            more = painterResource(R.drawable.ic_more_vert),
                            check = painterResource(R.drawable.ic_done),
                            bell = painterResource(R.drawable.ic_notifications),
                            rss = painterResource(R.drawable.ic_rss_feed),
                            controls = YpPlaylistControlIcons(
                                play = painterResource(R.drawable.ic_play_arrow),
                                shuffle = painterResource(R.drawable.ic_shuffle),
                                listen = painterResource(R.drawable.ic_headset)
                            )
                        ),
                        actions = actions()
                    )
                }
            }
        }
    }

    private fun actions() = ChannelActions(
        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        onSearch = { NavigationHelper.openSearchFragment(fm, viewModel.serviceId, "") },
        onShare = {
            viewModel.info?.let {
                ShareUtils.shareText(requireContext(), it.name, it.originalUrl, it.avatars)
            }
        },
        onOpenInBrowser = {
            viewModel.info?.let { ShareUtils.openUrlInBrowser(requireContext(), it.originalUrl) }
        },
        onSettings = { NavigationHelper.openSettings(requireContext()) },
        onToggleSubscription = viewModel::toggleSubscription,
        onToggleNotify = viewModel::toggleNotify,
        onRss = {
            viewModel.info?.feedUrl?.let { ShareUtils.openUrlInApp(requireContext(), it) }
        },
        onParentClick = ::openParentChannel,
        onTabSelect = viewModel::selectTab,
        onItemClick = ::openItem,
        onItemMenu = ::showItemMenu,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onRetryTab = viewModel::retry,
        onErrorAction = ::onErrorAction,
        onErrorOpenInBrowser = {
            viewModel.lastError?.openInBrowserUrl?.let {
                ShareUtils.openUrlInBrowser(requireContext(), it)
            }
        },
        onPlay = { play { NavigationHelper.playOnMainPlayer(activity, it) } },
        onShuffle = {
            viewModel.playQueue()?.let { queue ->
                queue.shuffle()
                NavigationHelper.playOnMainPlayer(activity, queue)
            }
        },
        onListen = {
            play { NavigationHelper.playOnBackgroundPlayer(requireContext(), it, false) }
        },
        onPlayLongClick = {
            viewModel.playQueue()?.let {
                NavigationHelper.enqueueOnPlayer(requireContext(), it, PlayerType.MAIN)
            }
        },
        onListenLongClick = {
            viewModel.playQueue()?.let {
                NavigationHelper.enqueueOnPlayer(requireContext(), it, PlayerType.AUDIO)
            }
        },
        onTagClick = { tag -> NavigationHelper.openSearchFragment(fm, viewModel.serviceId, tag) },
        onLinkClick = { link -> ShareUtils.openUrlInApp(requireContext(), link) }
    )

    /** Plays the tab's videos, with the "hold to enqueue" tip as before. */
    private fun play(start: (PlayQueue) -> Unit) {
        val queue = viewModel.playQueue() ?: return
        start(queue)
        if (PlayButtonHelper.shouldShowHoldToAppendTip(requireContext())) {
            Toast.makeText(requireContext(), R.string.hold_to_append, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openParentChannel() {
        val info = viewModel.info ?: return
        val parentUrl = info.parentChannelUrl
        if (parentUrl.isNullOrEmpty()) {
            return
        }
        try {
            NavigationHelper.openChannelFragment(
                fm,
                info.serviceId,
                parentUrl,
                info.parentChannelName.orEmpty()
            )
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e)
        }
    }

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
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel item", e)
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

    /** Offers the new-video notifications right after subscribing, as before. */
    private fun showNotifySnackbar() {
        val root = view ?: return
        Snackbar.make(root, R.string.you_successfully_subscribed, Snackbar.LENGTH_LONG)
            .setAction(R.string.get_notified) { viewModel.setNotify(true) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, viewModel.state.value.selectedTab)
        outState.putBoolean(STATE_FRONT_PAGE, useAsFrontPage || !viewModel.state.value.showBack)
    }

    companion object {
        private const val STATE_TAB = "yp_channel_tab"
        private const val STATE_FRONT_PAGE = "yp_channel_front_page"

        @JvmStatic
        fun getInstance(serviceId: Int, url: String?, name: String?): YpChannelFragment =
            YpChannelFragment().apply {
                arguments = Bundle().apply {
                    putInt(KEY_SERVICE_ID, serviceId)
                    putString(KEY_URL, url)
                    putString(KEY_TITLE, name.orEmpty())
                }
            }
    }
}
