package org.schabi.newpipe.fragments.list.comments;

import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.Collections;

public final class CommentRepliesInfo extends ListInfo<CommentsInfoItem> {
    /**
     * This class is used to wrap the comment replies page into a ListInfo object.
     *
     * @param comment the comment from which to get replies
     * @param name will be shown as the fragment title
     */
    public CommentRepliesInfo(final CommentsInfoItem comment, final String name) {
        super(comment.getServiceId(),
                new ListLinkHandler("", "", "", Collections.emptyList(), null), name);
        setNextPage(comment.getReplies());
        setRelatedItems(Collections.emptyList()); // since it must be non-null
    }
}
