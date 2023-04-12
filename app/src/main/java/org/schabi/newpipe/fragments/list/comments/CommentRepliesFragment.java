package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;

import java.util.Queue;

import io.reactivex.rxjava3.core.Single;

public final class CommentRepliesFragment
        extends BaseListInfoFragment<CommentsInfoItem, CommentRepliesInfo> {

    // the original comments info loaded alongside the stream
    private CommentsInfo commentsInfo;
    // the comment to show replies of
    private CommentsInfoItem commentsInfoItem;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    public CommentRepliesFragment() {
        super(UserAction.REQUESTED_COMMENT_REPLIES);
    }

    public CommentRepliesFragment(final CommentsInfo commentsInfo,
                                  final CommentsInfoItem commentsInfoItem) {
        this();
        this.commentsInfo = commentsInfo;
        this.commentsInfoItem = commentsInfoItem;
        setInitialData(commentsInfo.getServiceId(), commentsInfo.getUrl(), commentsInfo.getName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // State saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(commentsInfo);
        objectsToSave.add(commentsInfoItem);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        commentsInfo = (CommentsInfo) savedObjects.poll();
        commentsInfoItem = (CommentsInfoItem) savedObjects.poll();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<CommentRepliesInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> CommentRepliesInfo.getInfo(commentsInfoItem,
                // the reply count string will be shown as the activity title
                Localization.replyCount(requireContext(), commentsInfoItem.getReplyCount())));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<CommentsInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreCommentItems(serviceId, commentsInfo, currentNextPage);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.LIST;
    }
}
