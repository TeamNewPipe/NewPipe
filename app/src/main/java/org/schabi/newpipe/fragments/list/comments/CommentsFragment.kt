package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.paging.Pager
import androidx.paging.PagingConfig
import org.schabi.newpipe.compose.comment.CommentSection
import org.schabi.newpipe.compose.theme.AppTheme
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.util.NO_SERVICE_ID

class CommentsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val arguments = requireArguments()
        val serviceId = arguments.getInt(SERVICE_ID, NO_SERVICE_ID)
        val url = arguments.getString(URL)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val flow = remember(serviceId, url) {
                    Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                        CommentsSource(serviceId, url, null)
                    }.flow
                }

                AppTheme {
                    CommentSection(flow = flow)
                }
            }
        }
    }

    companion object {
        private const val SERVICE_ID = "serviceId"
        private const val URL = "url"

        @JvmStatic
        fun getInstance(serviceId: Int, url: String?) = CommentsFragment().apply {
            arguments = bundleOf(
                SERVICE_ID to serviceId,
                URL to url
            )
        }
    }
}
