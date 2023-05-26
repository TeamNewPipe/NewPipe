package org.schabi.newpipe.info_list.holder

import android.graphics.Paint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PicassoHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.text.CommentTextOnTouchListener
import org.schabi.newpipe.util.text.TextLinkifier
import java.util.function.Consumer

open class CommentsMiniInfoItemHolder internal constructor(
    infoItemBuilder: InfoItemBuilder,
    layoutId: Int,
    parent: ViewGroup?
) : InfoItemHolder(infoItemBuilder, layoutId, parent) {
    private val commentHorizontalPadding: Int
    private val commentVerticalPadding: Int
    private val paintAtContentSize: Paint
    private val ellipsisWidthPx: Float
    private val itemRoot: RelativeLayout = itemView.findViewById(R.id.itemRoot)
    private val itemThumbnailView: ImageView = itemView.findViewById(R.id.itemThumbnailView)
    private val itemContentView: TextView = itemView.findViewById(R.id.itemCommentContentView)
    private val itemLikesCountView: TextView = itemView.findViewById(R.id.detail_thumbs_up_count_view)
    private val itemPublishedTime: TextView = itemView.findViewById(R.id.itemPublishedTime)
    private val disposables = CompositeDisposable()
    private var commentText: Description? = null
    private var streamService: StreamingService? = null
    private var streamUrl: String? = null

    init {
        commentHorizontalPadding = infoItemBuilder.context
            .resources.getDimension(R.dimen.comments_horizontal_padding).toInt()
        commentVerticalPadding = infoItemBuilder.context
            .resources.getDimension(R.dimen.comments_vertical_padding).toInt()
        paintAtContentSize = Paint()
        paintAtContentSize.textSize = itemContentView.textSize
        ellipsisWidthPx = paintAtContentSize.measureText(ELLIPSIS)
    }

    constructor(
        infoItemBuilder: InfoItemBuilder,
        parent: ViewGroup?
    ) : this(infoItemBuilder, R.layout.list_comments_mini_item, parent) {
    }

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        if (infoItem !is CommentsInfoItem) {
            return
        }
        PicassoHelper.loadAvatar(infoItem.uploaderAvatarUrl).into(itemThumbnailView)
        if (PicassoHelper.getShouldLoadImages()) {
            itemThumbnailView.visibility = View.VISIBLE
            itemRoot.setPadding(
                commentVerticalPadding, commentVerticalPadding,
                commentVerticalPadding, commentVerticalPadding
            )
        } else {
            itemThumbnailView.visibility = View.GONE
            itemRoot.setPadding(
                commentHorizontalPadding, commentVerticalPadding,
                commentHorizontalPadding, commentVerticalPadding
            )
        }
        itemThumbnailView.setOnClickListener { openCommentAuthor(infoItem) }
        streamService = try {
            NewPipe.getService(infoItem.serviceId)
        } catch (e: ExtractionException) {
            // should never happen
            showUiErrorSnackbar(itemBuilder.context, "Getting StreamingService", e)
            Log.w(TAG, "Cannot obtain service from comment service id, defaulting to YouTube", e)
            ServiceList.YouTube
        }
        streamUrl = infoItem.url
        commentText = infoItem.commentText
        ellipsize()
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.INSTANCE)
        if (infoItem.likeCount >= 0) {
            itemLikesCountView.text = Localization.shortCount(
                itemBuilder.context,
                infoItem.likeCount.toLong()
            )
        } else {
            itemLikesCountView.text = "-"
        }
        if (infoItem.uploadDate != null) {
            itemPublishedTime.text = Localization.relativeTime(
                infoItem.uploadDate!!
                    .offsetDateTime()
            )
        } else {
            itemPublishedTime.text = infoItem.textualUploadDate
        }
        itemView.setOnClickListener {
            toggleEllipsize()
            if (itemBuilder.onCommentsSelectedListener != null) {
                itemBuilder.onCommentsSelectedListener.selected(infoItem)
            }
        }
        itemView.setOnLongClickListener {
            if (DeviceUtils.isTv(itemBuilder.context)) {
                openCommentAuthor(infoItem)
            } else {
                val text = itemContentView.text
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.context, text.toString())
                }
            }
            true
        }
    }

    private fun openCommentAuthor(item: CommentsInfoItem) {
        if (TextUtils.isEmpty(item.uploaderUrl)) {
            return
        }
        val activity = itemBuilder.context as AppCompatActivity
        try {
            NavigationHelper.openChannelFragment(
                activity.supportFragmentManager,
                item.serviceId,
                item.uploaderUrl,
                item.uploaderName
            )
        } catch (e: Exception) {
            showUiErrorSnackbar(activity, "Opening channel fragment", e)
        }
    }

    private fun allowLinkFocus() {
        itemContentView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun denyLinkFocus() {
        itemContentView.movementMethod = null
    }

    private fun shouldFocusLinks(): Boolean {
        if (itemView.isInTouchMode) {
            return false
        }
        val urls = itemContentView.urls
        return urls != null && urls.isNotEmpty()
    }

    private fun determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus()
        } else {
            denyLinkFocus()
        }
    }

    private fun ellipsize() {
        itemContentView.maxLines = COMMENT_EXPANDED_LINES
        linkifyCommentContentView {
            var hasEllipsis = false
            val charSeqText = itemContentView.text
            if (charSeqText != null && itemContentView.lineCount > COMMENT_DEFAULT_LINES) {
                // Note that converting to String removes spans (i.e. links), but that's something
                // we actually want since when the text is ellipsized we want all clicks on the
                // comment to expand the comment, not to open links.
                val text = charSeqText.toString()
                val layout = itemContentView.layout
                val lineWidth = layout.getLineWidth(COMMENT_DEFAULT_LINES - 1)
                val layoutWidth = layout.width.toFloat()
                val lineStart = layout.getLineStart(COMMENT_DEFAULT_LINES - 1)
                val lineEnd = layout.getLineEnd(COMMENT_DEFAULT_LINES - 1)

                // remove characters up until there is enough space for the ellipsis
                // (also summing 2 more pixels, just to be sure to avoid float rounding errors)
                var end = lineEnd
                var removedCharactersWidth = 0.0f
                while (lineWidth - removedCharactersWidth + ellipsisWidthPx + 2.0f > layoutWidth &&
                    end >= lineStart
                ) {
                    end -= 1
                    // recalculate each time to account for ligatures or other similar things
                    removedCharactersWidth = paintAtContentSize.measureText(
                        text.substring(end, lineEnd)
                    )
                }

                // remove trailing spaces and newlines
                while (end > 0 && Character.isWhitespace(text[end - 1])) {
                    end -= 1
                }
                val newVal = text.substring(0, end) + ELLIPSIS
                itemContentView.text = newVal
                hasEllipsis = true
            }
            itemContentView.maxLines = COMMENT_DEFAULT_LINES
            if (hasEllipsis) {
                denyLinkFocus()
            } else {
                determineMovementMethod()
            }
        }
    }

    private fun toggleEllipsize() {
        val text = itemContentView.text
        if (!TextUtils.isEmpty(text) && text[text.length - 1] == ELLIPSIS[0]) {
            expand()
        } else if (itemContentView.lineCount > COMMENT_DEFAULT_LINES) {
            ellipsize()
        }
    }

    private fun expand() {
        itemContentView.maxLines = COMMENT_EXPANDED_LINES
        linkifyCommentContentView { determineMovementMethod() }
    }

    private fun linkifyCommentContentView(onCompletion: Consumer<TextView>?) {
        disposables.clear()
        if (commentText != null) {
            TextLinkifier.fromDescription(
                itemContentView, commentText!!,
                HtmlCompat.FROM_HTML_MODE_LEGACY, streamService, streamUrl, disposables,
                onCompletion
            )
        }
    }

    companion object {
        private const val TAG = "CommentsMiniIIHolder"
        private const val ELLIPSIS = "…"
        private const val COMMENT_DEFAULT_LINES = 2
        private const val COMMENT_EXPANDED_LINES = 1000
    }
}
