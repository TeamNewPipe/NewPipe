package org.schabi.newpipe.fragments.list.comments

import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

class CommentRepliesInfo(comment: CommentsInfoItem?, name: String?) : ListInfo<CommentsInfoItem?>(comment!!.getServiceId(),
        ListLinkHandler("", "", "", emptyList(), null), name) {
    /**
     * This class is used to wrap the comment replies page into a ListInfo object.
     *
     * @param comment the comment from which to get replies
     * @param name will be shown as the fragment title
     */
    init {
        setNextPage(comment!!.getReplies())
        setRelatedItems(emptyList<CommentsInfoItem>()) // since it must be non-null
    }
}
