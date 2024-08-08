package org.schabi.newpipe.fragments.list.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.ui.components.video.RelatedItems
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_INFO

class RelatedItemsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        RelatedItems(requireArguments().serializable<StreamInfo>(KEY_INFO)!!)
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(info: StreamInfo) = RelatedItemsFragment().apply {
            arguments = bundleOf(KEY_INFO to info)
        }
    }
}
