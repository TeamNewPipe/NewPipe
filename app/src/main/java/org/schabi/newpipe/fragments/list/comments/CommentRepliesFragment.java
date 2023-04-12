package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
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

import java.util.Queue;

import io.reactivex.rxjava3.core.Single;

public final class CommentRepliesFragment
        extends BaseListInfoFragment<CommentsInfoItem, CommentRepliesInfo> {

    // has the same content as super.currentInfo, except that it's never null
    private CommentRepliesInfo commentRepliesInfo;
    // the original comments info loaded alongside stream
    private CommentsInfo commentsInfo;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    public CommentRepliesFragment() {
        super(UserAction.REQUESTED_COMMENT_REPLIES);
    }

    public CommentRepliesFragment(final CommentsInfo commentsInfo,
                                  final CommentsInfoItem commentsInfoItem) {
        this();
        this.commentRepliesInfo = CommentRepliesInfo.getInfo(commentsInfoItem);
        this.commentsInfo = commentsInfo;
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
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(commentRepliesInfo);
        objectsToSave.add(commentsInfo);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        commentRepliesInfo = (CommentRepliesInfo) savedObjects.poll();
        commentsInfo = (CommentsInfo) savedObjects.poll();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<CommentRepliesInfo> loadResult(final boolean forceLoad) {
        return Single.just(this.commentRepliesInfo);
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
