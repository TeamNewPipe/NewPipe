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
import org.schabi.newpipe.util.Constants;

import icepick.State;

public class CommentReplyDialog extends BottomSheetDialogFragment {

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;
    @State
    protected Page replies;

    public static CommentReplyDialog getInstance(final int serviceId, final String url,
                                                 final String name, final Page replies) {
        final CommentReplyDialog instance = new CommentReplyDialog();
        instance.setInitialData(serviceId, url, name, replies);
        return instance;
    }

    public static void show(final FragmentManager fragmentManager,
                            final int serviceId, final String url,
                            final String name, final Page replies) {
        final CommentReplyDialog instance = getInstance(serviceId, url, name, replies);
        instance.show(fragmentManager, instance.getTag());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.dialog_comment_reply, container,
                false);
        final ImageButton backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> dismiss());
        final CommentsFragment commentsFragment = CommentsFragment.getInstance(
                serviceId, url, name, replies
        );
        getChildFragmentManager().beginTransaction()
                .add(R.id.commentFragment, commentsFragment).commit();
        return view;
    }

    protected void setInitialData(final int sid,
                                  final String u, final String title, final Page repliesPage) {
        this.serviceId = sid;
        this.url = u;
        this.name = !TextUtils.isEmpty(title) ? title : "";
        this.replies = repliesPage;
    }
}
