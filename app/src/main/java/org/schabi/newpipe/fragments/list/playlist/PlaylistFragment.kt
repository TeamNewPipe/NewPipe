package org.schabi.newpipe.fragments.list.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.screens.PlaylistScreen
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.viewmodels.PlaylistViewModel

class PlaylistFragment : Fragment() {
    private val viewModel by viewModels<PlaylistViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        AppTheme {
            Surface {
                PlaylistScreen(viewModel)
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val activity = requireActivity()

        (activity as? AppCompatActivity)?.supportActionBar?.let {
            it.setDisplayShowTitleEnabled(true)
            it.title = viewModel.playlistTitle
        }

        activity.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) {
                    menuInflater.inflate(R.menu.menu_playlist, menu)

                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.bookmarkFlow.collectLatest {
                                val bookmarkButton = menu.findItem(R.id.menu_item_bookmark)
                                bookmarkButton.setIcon(if (it == null) R.drawable.ic_playlist_add else R.drawable.ic_playlist_add_check)
                                bookmarkButton.setTitle(if (it == null) R.string.bookmark_playlist else R.string.unbookmark_playlist)
                            }
                        }
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.action_settings -> {
                            NavigationHelper.openSettings(activity)
                        }
                        R.id.menu_item_openInBrowser -> {
                            ShareUtils.openUrlInBrowser(activity, viewModel.url)
                        }
                        R.id.menu_item_bookmark -> {
                            viewModel.toggleBookmark()
                        }
                        R.id.menu_item_share -> {
                            ShareUtils.shareText(activity, viewModel.playlistTitle, viewModel.url)
                        }
                    }
                    return true
                }
            },
            viewLifecycleOwner,
        )
    }

    companion object {
        @JvmStatic
        fun getInstance(
            serviceId: Int,
            url: String,
            playlistName: String,
        ) = PlaylistFragment().apply {
            arguments = bundleOf(
                KEY_SERVICE_ID to serviceId,
                KEY_URL to url,
                KEY_TITLE to playlistName,
            )
        }
    }
}
