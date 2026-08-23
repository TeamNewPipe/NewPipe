package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.util.Constants;

import java.io.IOException;
import java.util.Objects;

public class CommentsFragmentContainer extends BaseFragment implements BackPressable {

    protected int serviceId = Constants.NO_SERVICE_ID;
    protected String url;
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("serviceId", serviceId);
        outState.putString("url", url);
        outState.putString("name", name);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceId = savedInstanceState.getInt("serviceId", Constants.NO_SERVICE_ID);
        url = savedInstanceState.getString("url");
        name = savedInstanceState.getString("name");
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, @Nullable final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_container, container, false);
        if (savedInstanceState == null) {
            setFragment(getChildFragmentManager(), serviceId, url, name);
        }
        return view;
    }

    public void update(final int serviceId, final String url, final String name) {
        if (!isAdded() || isRemoving()) {
            return;
        }
        if (this.serviceId == serviceId
                && Objects.equals(this.url, url)
                && Objects.equals(this.name, name)) {
            return;
        }
        this.serviceId = serviceId;
        this.url = url;
        this.name = name;
        setFragment(getChildFragmentManager(), serviceId, url, name);
    }

    @Override
    public boolean onBackPressed() {
        // Replies are pushed onto our OWN child FragmentManager (tied to this fragment's view), so
        // backing out of a reply pops here. When the view is gone (e.g. video sent to pop-up) the
        // child FM is torn down with it, so there's no stale entry to recreate -> no crash on back.
        final FragmentManager childFm = getChildFragmentManager();
        if (childFm.getBackStackEntryCount() > 0) {
            childFm.popBackStack();
            return true;
        }
        return false;
    }

    public static void setFragment(
            FragmentManager fm,
            int sid, String u, String title) {

        if (fm == null) {
            return;
        }

        if (fm.isStateSaved()) {
            return;
        }

        Fragment existing = fm.findFragmentById(R.id.fragment_container_view);
        if (existing instanceof CommentsFragment) {
            CommentsFragment cf = (CommentsFragment) existing;
            if (cf.getServiceId() == sid && cf.getUrl().equals(u) && cf.getName().equals(title)) {
                return;
            }
        }

        CommentsFragment fragment = CommentsFragment.getInstance(sid, u, title);
        fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container_view, fragment)
                .commitAllowingStateLoss();
    }

    public static void setFragment(
            final FragmentManager fm, final CommentsInfoItem comment
    ) throws IOException, ClassNotFoundException {
        if (fm == null || fm.isStateSaved()) {
            return;
        }
        final Page reply = comment.getReplies();
        final CommentReplyFragment fragment = CommentReplyFragment.getInstance(
                comment.getServiceId(), comment.getUrl(),
                comment.getName(), comment, reply
        );
        fm.beginTransaction()
                .replace(R.id.fragment_container_view, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }
}
