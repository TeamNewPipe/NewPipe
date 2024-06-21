package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.paging.Pager
import androidx.paging.PagingConfig
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.ktx.serializable
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization

class CommentRepliesFragment : Fragment() {
    lateinit var comment: CommentsInfoItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        comment = requireArguments().serializable<CommentsInfoItem>(COMMENT_KEY)!!

        val activity = requireActivity() as AppCompatActivity
        val bar = activity.supportActionBar!!
        bar.setDisplayShowTitleEnabled(true)
        bar.title = Localization.replyCount(activity, comment.replyCount)

        return ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val flow = remember(comment) {
                    Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                        CommentsSource(comment.serviceId, comment.url, comment.replies)
                    }.flow
                }

                AppTheme {
                    CommentSection(parentComment = comment, flow = flow)
                }
            }
        }
    }

    companion object {
        @JvmField
        val TAG = CommentRepliesFragment::class.simpleName!!

        const val COMMENT_KEY = "comment"
    }
}
