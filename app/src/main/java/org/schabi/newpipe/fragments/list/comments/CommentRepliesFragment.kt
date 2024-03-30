package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.CommentRepliesHeaderBinding
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.util.text.TextLinkifier
import java.util.Queue
import java.util.concurrent.Callable
import java.util.function.Supplier

class CommentRepliesFragment  /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    ////////////////////////////////////////////////////////////////////////// */
// only called by the Android framework, after which readFrom is called and restores all data
() : BaseListInfoFragment<CommentsInfoItem?, CommentRepliesInfo>(UserAction.REQUESTED_COMMENT_REPLIES) {
    private var commentsInfoItem: CommentsInfoItem? = null // the comment to show replies of
    private val disposables: CompositeDisposable = CompositeDisposable()

    constructor(commentsInfoItem: CommentsInfoItem) : this() {
        this.commentsInfoItem = commentsInfoItem
        // setting "" as title since the title will be properly set right after
        setInitialData(commentsInfoItem.getServiceId(), commentsInfoItem.getUrl(), "")
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_comments, container, false)
    }

    public override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    override fun getListHeaderSupplier(): Supplier<View>? {
        return Supplier({
            val binding: CommentRepliesHeaderBinding = CommentRepliesHeaderBinding
                    .inflate(activity!!.getLayoutInflater(), itemsList, false)
            val item: CommentsInfoItem? = commentsInfoItem

            // load the author avatar
            PicassoHelper.loadAvatar(item!!.getUploaderAvatars()).into(binding.authorAvatar)
            binding.authorAvatar.setVisibility(if (ImageStrategy.shouldLoadImages()) View.VISIBLE else View.GONE)

            // setup author name and comment date
            binding.authorName.setText(item.getUploaderName())
            binding.uploadDate.setText(Localization.relativeTimeOrTextual(
                    getContext(), item.getUploadDate(), item.getTextualUploadDate()))
            binding.authorTouchArea.setOnClickListener(
                    View.OnClickListener({ v: View? -> NavigationHelper.openCommentAuthorIfPresent(requireActivity(), (item)) }))

            // setup like count, hearted and pinned
            binding.thumbsUpCount.setText(
                    Localization.likeCount(requireContext(), item.getLikeCount()))
            // for heartImage goneMarginEnd was used, but there is no way to tell ConstraintLayout
            // not to use a different margin only when both the next two views are gone
            (binding.thumbsUpCount.getLayoutParams() as ConstraintLayout.LayoutParams)
                    .setMarginEnd(DeviceUtils.dpToPx(
                            (if (item.isHeartedByUploader() || item.isPinned()) 8 else 16),
                            requireContext()))
            binding.heartImage.setVisibility(if (item.isHeartedByUploader()) View.VISIBLE else View.GONE)
            binding.pinnedImage.setVisibility(if (item.isPinned()) View.VISIBLE else View.GONE)

            // setup comment content
            TextLinkifier.fromDescription(binding.commentContent, item.getCommentText(),
                    HtmlCompat.FROM_HTML_MODE_LEGACY, ServiceHelper.getServiceById(item.getServiceId()),
                    item.getUrl(), disposables, null)
            binding.getRoot()
        })
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State saving
    ////////////////////////////////////////////////////////////////////////// */
    public override fun writeTo(objectsToSave: Queue<Any?>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(commentsInfoItem)
    }

    @Throws(Exception::class)
    public override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        commentsInfoItem = savedObjects.poll() as CommentsInfoItem?
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    ////////////////////////////////////////////////////////////////////////// */
    override fun loadResult(forceLoad: Boolean): Single<CommentRepliesInfo>? {
        return Single.fromCallable(Callable({
            CommentRepliesInfo(commentsInfoItem,  // the reply count string will be shown as the activity title
                    Localization.replyCount(requireContext(), commentsInfoItem!!.getReplyCount()))
        }))
    }

    override fun loadMoreItemsLogic(): Single<InfoItemsPage<CommentsInfoItem?>?>? {
        // commentsInfoItem.getUrl() should contain the url of the original
        // ListInfo<CommentsInfoItem>, which should be the stream url
        return ExtractorHelper.getMoreCommentItems(
                serviceId, commentsInfoItem!!.getUrl(), currentNextPage)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    override fun getItemViewMode(): ItemViewMode? {
        return ItemViewMode.LIST
    }

    /**
     * @return the comment to which the replies are shown
     */
    fun getCommentsInfoItem(): CommentsInfoItem? {
        return commentsInfoItem
    }

    companion object {
        val TAG: String = CommentRepliesFragment::class.java.getSimpleName()
    }
}
