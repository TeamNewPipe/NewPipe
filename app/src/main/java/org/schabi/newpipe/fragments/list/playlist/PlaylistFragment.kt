package org.schabi.newpipe.fragments.list.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.schabi.newpipe.ui.screens.PlaylistScreen
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL

class PlaylistFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        AppTheme {
            PlaylistScreen()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(serviceId: Int, url: String?) = PlaylistFragment().apply {
            arguments = bundleOf(KEY_SERVICE_ID to serviceId, KEY_URL to url)
        }
    }
}
