package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;

public class EmptyFragment extends BaseFragment {
    final boolean showMessage;

    public EmptyFragment(final boolean showMessage) {
        this.showMessage = showMessage;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_empty, container, false);
        view.findViewById(R.id.empty_state_view).setVisibility(
                showMessage ? View.VISIBLE : View.GONE);
        return view;
    }
}
