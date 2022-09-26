package org.schabi.newpipe.fragments.list.comments;

import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.util.SerializedUtils;

final class CommentUtils {
    private CommentUtils() {
    }

    public static CommentsInfo clone(final CommentsInfo item) throws Exception {
        return SerializedUtils.clone(item, CommentsInfo.class);
    }

    public static CommentsInfoItem clone(final CommentsInfoItem item) throws Exception {
        return SerializedUtils.clone(item, CommentsInfoItem.class);
    }
}
