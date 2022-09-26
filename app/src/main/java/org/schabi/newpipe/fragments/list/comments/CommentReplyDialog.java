package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.util.Constants;

public class CommentReplyDialog extends BottomSheetDialogFragment {

    private int serviceId = Constants.NO_SERVICE_ID;
    private String name;
    private String url;
    private CommentsInfoItem comment;
    private Page replies;

    public static CommentReplyDialog getInstance(final int serviceId, final String url,
                                                 final String name,
                                                 final CommentsInfoItem comment,
                                                 final Page replies) throws Exception {
        final CommentReplyDialog instance = new CommentReplyDialog();
        instance.setInitialData(serviceId, url, name, comment, replies);
        return instance;
    }

    public static void show(
            final FragmentManager fragmentManager,
            final CommentsInfoItem comment) throws Exception {
        final Page reply = comment.getReplies();
        final CommentReplyDialog instance = getInstance(comment.getServiceId(),
                comment.getUrl(), comment.getName(), comment, reply);
        instance.show(fragmentManager, instance.getTag());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.dialog_comment_reply, container,
                false);
        if (serviceId == Constants.NO_SERVICE_ID) {
            dismiss();
            return view;
        }
        final ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> dismiss());
        final CommentsFragment commentsFragment = CommentsFragment.getInstance(
                serviceId, url, name, comment
        );
        final CommentsFragment commentsReplyFragment = CommentsFragment.getInstance(
                serviceId, url, name, replies
        );
        getChildFragmentManager().beginTransaction()
                .add(R.id.commentFragment, commentsFragment).commit();
        getChildFragmentManager().beginTransaction()
                .add(R.id.commentReplyFragment, commentsReplyFragment).commit();
        return view;
    }

    protected void setInitialData(final int sid, final String u, final String title,
                                  final CommentsInfoItem preComment,
                                  final Page repliesPage) throws Exception {
        this.serviceId = sid;
        this.url = u;
        this.name = !TextUtils.isEmpty(title) ? title : "";
        // clone comment object to avoid replies actually set null
        this.comment = CommentUtils.clone(preComment);
        comment.setReplies(null);
        this.replies = repliesPage;
    }
}
