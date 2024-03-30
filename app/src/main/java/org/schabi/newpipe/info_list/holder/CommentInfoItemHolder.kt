package org.schabi.newpipe.info_list.holder

import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.util.text.CommentTextOnTouchListener
import org.schabi.newpipe.util.text.TextEllipsizer
import java.util.function.Consumer

class CommentInfoItemHolder(infoItemBuilder: InfoItemBuilder,
                            parent: ViewGroup?) : InfoItemHolder(infoItemBuilder, R.layout.list_comment_item, parent) {
    private val commentHorizontalPadding: Int
    private val commentVerticalPadding: Int
    private val itemRoot: RelativeLayout
    private val itemThumbnailView: ImageView
    private val itemContentView: TextView
    private val itemThumbsUpView: ImageView
    private val itemLikesCountView: TextView
    private val itemTitleView: TextView
    private val itemHeartView: ImageView
    private val itemPinnedView: ImageView
    private val repliesButton: Button
    private val textEllipsizer: TextEllipsizer

    init {
        itemRoot = itemView.findViewById(R.id.itemRoot)
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView)
        itemContentView = itemView.findViewById(R.id.itemCommentContentView)
        itemThumbsUpView = itemView.findViewById(R.id.detail_thumbs_up_img_view)
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view)
        itemTitleView = itemView.findViewById(R.id.itemTitleView)
        itemHeartView = itemView.findViewById(R.id.detail_heart_image_view)
        itemPinnedView = itemView.findViewById(R.id.detail_pinned_view)
        repliesButton = itemView.findViewById(R.id.replies_button)
        commentHorizontalPadding = infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding).toInt()
        commentVerticalPadding = infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding).toInt()
        textEllipsizer = TextEllipsizer(itemContentView, COMMENT_DEFAULT_LINES, null)
        textEllipsizer.setStateChangeListener(Consumer({ isEllipsized: Boolean ->
            if ((java.lang.Boolean.TRUE == isEllipsized)) {
                denyLinkFocus()
            } else {
                determineMovementMethod()
            }
        }))
    }

    public override fun updateFromItem(infoItem: InfoItem?,
                                       historyRecordManager: HistoryRecordManager) {
        if (!(infoItem is CommentsInfoItem)) {
            return
        }
        val item: CommentsInfoItem = infoItem


        // load the author avatar
        PicassoHelper.loadAvatar(item.getUploaderAvatars()).into(itemThumbnailView)
        if (ImageStrategy.shouldLoadImages()) {
            itemThumbnailView.setVisibility(View.VISIBLE)
            itemRoot.setPadding(commentVerticalPadding, commentVerticalPadding,
                    commentVerticalPadding, commentVerticalPadding)
        } else {
            itemThumbnailView.setVisibility(View.GONE)
            itemRoot.setPadding(commentHorizontalPadding, commentVerticalPadding,
                    commentHorizontalPadding, commentVerticalPadding)
        }
        itemThumbnailView.setOnClickListener(View.OnClickListener({ view: View? -> openCommentAuthor(item) }))


        // setup the top row, with pinned icon, author name and comment date
        itemPinnedView.setVisibility(if (item.isPinned()) View.VISIBLE else View.GONE)
        itemTitleView.setText(Localization.concatenateStrings(item.getUploaderName(),
                Localization.relativeTimeOrTextual(itemBuilder.getContext(), item.getUploadDate(),
                        item.getTextualUploadDate())))


        // setup bottom row, with likes, heart and replies button
        itemLikesCountView.setText(
                Localization.likeCount(itemBuilder.getContext(), item.getLikeCount()))
        itemHeartView.setVisibility(if (item.isHeartedByUploader()) View.VISIBLE else View.GONE)
        val hasReplies: Boolean = item.getReplies() != null
        repliesButton.setOnClickListener(if (hasReplies) View.OnClickListener({ v: View? -> openCommentReplies(item) }) else null)
        repliesButton.setVisibility(if (hasReplies) View.VISIBLE else View.GONE)
        repliesButton.setText(if (hasReplies) Localization.replyCount(itemBuilder.getContext(), item.getReplyCount()) else "")
        (itemThumbsUpView.getLayoutParams() as RelativeLayout.LayoutParams).topMargin = if (hasReplies) 0 else DeviceUtils.dpToPx(6, itemBuilder.getContext())


        // setup comment content and click listeners to expand/ellipsize it
        textEllipsizer.setStreamingService(ServiceHelper.getServiceById(item.getServiceId()))
        textEllipsizer.setStreamUrl(item.getUrl())
        textEllipsizer.setContent(item.getCommentText())
        textEllipsizer.ellipsize()
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.Companion.INSTANCE)
        itemView.setOnClickListener(View.OnClickListener({ view: View? ->
            textEllipsizer.toggle()
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener()!!.selected(item)
            }
        }))
        itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (DeviceUtils.isTv(itemBuilder.getContext())) {
                openCommentAuthor(item)
            } else {
                val text: CharSequence? = itemContentView.getText()
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.getContext(), text.toString())
                }
            }
            true
        }))
    }

    private fun openCommentAuthor(item: CommentsInfoItem) {
        NavigationHelper.openCommentAuthorIfPresent((itemBuilder.getContext() as FragmentActivity?)!!,
                item)
    }

    private fun openCommentReplies(item: CommentsInfoItem) {
        NavigationHelper.openCommentRepliesFragment((itemBuilder.getContext() as FragmentActivity?)!!,
                item)
    }

    private fun allowLinkFocus() {
        itemContentView.setMovementMethod(LinkMovementMethod.getInstance())
    }

    private fun denyLinkFocus() {
        itemContentView.setMovementMethod(null)
    }

    private fun shouldFocusLinks(): Boolean {
        if (itemView.isInTouchMode()) {
            return false
        }
        val urls: Array<URLSpan>? = itemContentView.getUrls()
        return urls != null && urls.size != 0
    }

    private fun determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus()
        } else {
            denyLinkFocus()
        }
    }

    companion object {
        private val COMMENT_DEFAULT_LINES: Int = 2
    }
}
