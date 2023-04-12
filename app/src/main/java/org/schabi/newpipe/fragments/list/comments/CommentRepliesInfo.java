package org.schabi.newpipe.fragments.list.comments;

import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.Collections;

public final class CommentRepliesInfo extends ListInfo<CommentsInfoItem> {
    private CommentRepliesInfo(final int serviceId,
                               final ListLinkHandler listUrlIdHandler,
                               final String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static CommentRepliesInfo getInfo(final CommentsInfoItem comment, final String name) {
        final ListLinkHandler handler =
                new ListLinkHandler("", "", "", Collections.emptyList(), null);
        final CommentRepliesInfo relatedItemInfo = new CommentRepliesInfo(
                comment.getServiceId(), handler, name); // the name will be shown as fragment title
        relatedItemInfo.setNextPage(comment.getReplies());
        relatedItemInfo.setRelatedItems(Collections.emptyList()); // since it must be non-null
        return relatedItemInfo;
    }
}
