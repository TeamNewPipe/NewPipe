package org.schabi.newpipe.settings.presentation.history_cache

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL

class HistoryCacheFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppTheme {
                HistoryCacheSettingsScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(serviceId: Int, url: String?) = CommentsFragment().apply {
            arguments = bundleOf(KEY_SERVICE_ID to serviceId, KEY_URL to url)
        }
    }
}
