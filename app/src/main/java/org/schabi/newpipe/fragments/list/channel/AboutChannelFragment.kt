package org.schabi.newpipe.fragments.list.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.ktx.parcelable
import org.schabi.newpipe.ui.components.channel.AboutChannelSection
import org.schabi.newpipe.ui.components.channel.ParcelableChannelInfo
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_INFO

class AboutChannelFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        AppTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                AboutChannelSection(requireArguments().parcelable(KEY_INFO)!!)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(channelInfo: ChannelInfo) = AboutChannelFragment().apply {
            arguments = bundleOf(KEY_INFO to ParcelableChannelInfo(channelInfo))
        }
    }
}
