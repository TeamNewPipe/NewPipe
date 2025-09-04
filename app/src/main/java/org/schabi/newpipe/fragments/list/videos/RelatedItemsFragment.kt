package org.schabi.newpipe.fragments.list.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.ui.components.video.RelatedItems
import org.schabi.newpipe.ui.theme.AppTheme

class RelatedItemsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        AppTheme {
            Surface {
                RelatedItems(viewModel(requireParentFragment()))
            }
        }
    }
}
