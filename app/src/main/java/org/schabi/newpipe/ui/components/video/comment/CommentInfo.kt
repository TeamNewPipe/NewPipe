package org.schabi.newpipe.ui.components.video.comment

import androidx.compose.runtime.Immutable
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

@Immutable
class CommentInfo(
    val serviceId: Int,
    val url: String,
    val comments: List<CommentsInfoItem>,
    val nextPage: Page?,
    val commentCount: Int,
    val isCommentsDisabled: Boolean
) {
    constructor(commentsInfo: CommentsInfo) : this(
        commentsInfo.serviceId,
        commentsInfo.url,
        commentsInfo.relatedItems,
        commentsInfo.nextPage,
        commentsInfo.commentsCount,
        commentsInfo.isCommentsDisabled
    )
}
