package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import org.schabi.newpipe.ui.components.comment.CommentSection
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.CommentsViewModel

class CommentsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val viewModel = viewModel<CommentsViewModel>()
            AppTheme {
                CommentSection(commentsFlow = viewModel.comments)
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
