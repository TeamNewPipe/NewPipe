package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.ui.components.video.StreamDescriptionSection
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_INFO

class DescriptionFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        AppTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                StreamDescriptionSection(requireArguments().serializable(KEY_INFO)!!)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(streamInfo: StreamInfo) = DescriptionFragment().apply {
            arguments = bundleOf(KEY_INFO to streamInfo)
        }
    }
}
