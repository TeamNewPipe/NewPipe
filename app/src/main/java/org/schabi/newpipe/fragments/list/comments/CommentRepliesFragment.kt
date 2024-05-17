package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import icepick.State
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import java.util.Queue
import java.util.function.Supplier

class CommentRepliesFragment() : BaseListInfoFragment<CommentsInfoItem, CommentRepliesInfo>(UserAction.REQUESTED_COMMENT_REPLIES) {
    /**
     * @return the comment to which the replies are shown
     */
    @State
    lateinit var commentsInfoItem: CommentsInfoItem // the comment to show replies of
    private val disposables = CompositeDisposable()

    constructor(commentsInfoItem: CommentsInfoItem) : this() {
        this.commentsInfoItem = commentsInfoItem
        // setting "" as title since the title will be properly set right after
        setInitialData(commentsInfoItem.serviceId, commentsInfoItem.url, "")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    override fun getListHeaderSupplier(): Supplier<View> {
        return Supplier {
            ComposeView(requireContext()).apply {
                setContent {
                    AppTheme {
                        CommentRepliesHeader(commentsInfoItem, disposables)
                    }
                }
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State saving
    ////////////////////////////////////////////////////////////////////////// */
    override fun writeTo(objectsToSave: Queue<Any>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(commentsInfoItem)
    }

    @Throws(Exception::class)
    override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        commentsInfoItem = savedObjects.poll() as CommentsInfoItem
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    ////////////////////////////////////////////////////////////////////////// */
    override fun loadResult(forceLoad: Boolean): Single<CommentRepliesInfo> {
        return Single.fromCallable {
            CommentRepliesInfo(
                commentsInfoItem, // the reply count string will be shown as the activity title
                Localization.replyCount(requireContext(), commentsInfoItem.replyCount)
            )
        }
    }

    override fun loadMoreItemsLogic(): Single<InfoItemsPage<CommentsInfoItem?>>? {
        // commentsInfoItem.getUrl() should contain the url of the original
        // ListInfo<CommentsInfoItem>, which should be the stream url
        return ExtractorHelper.getMoreCommentItems(
            serviceId, commentsInfoItem.url, currentNextPage
        )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    override fun getItemViewMode(): ItemViewMode {
        return ItemViewMode.LIST
    }

    companion object {
        @JvmField
        val TAG: String = CommentRepliesFragment::class.java.simpleName
    }
}
