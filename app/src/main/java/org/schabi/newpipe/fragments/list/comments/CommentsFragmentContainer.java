package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.util.Constants;

import java.io.IOException;

import icepick.State;


public class CommentsFragmentContainer extends BaseFragment {

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String url;
    @State
    protected String name;

    public static CommentsFragmentContainer getInstance(
            final int serviceId, final String url, final String name) {
        final CommentsFragmentContainer fragment = new CommentsFragmentContainer();
        fragment.serviceId = serviceId;
        fragment.url = url;
        fragment.name = name;
        return fragment;
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, @Nullable final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_container, container, false);
        setFragment(getFM(), serviceId, url, name);
        return view;
    }

    public static void setFragment(
            final FragmentManager fm,
            final int sid, final String u, final String title) {
        final CommentsFragment fragment = CommentsFragment.getInstance(
                sid, u, title
        );
        fm.beginTransaction().add(R.id.fragment_container_view, fragment).commit();
    }

    public static void setFragment(
            final FragmentManager fm, final CommentsInfoItem comment
    ) throws IOException, ClassNotFoundException {
        final Page reply = comment.getReplies();
        final CommentReplyFragment fragment = CommentReplyFragment.getInstance(
                comment.getServiceId(), comment.getUrl(),
                comment.getName(), comment, reply
        );
        fm.beginTransaction().add(R.id.fragment_container_view, fragment).commit();
    }
}
