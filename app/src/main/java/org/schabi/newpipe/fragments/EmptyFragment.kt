package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ui.emptystate.EmptyStateUtil;

public class EmptyFragment extends BaseFragment {
    private static final String SHOW_MESSAGE = "SHOW_MESSAGE";

    public static final EmptyFragment newInstance(final boolean showMessage) {
        final EmptyFragment emptyFragment = new EmptyFragment();
        final Bundle bundle = new Bundle(1);
        bundle.putBoolean(SHOW_MESSAGE, showMessage);
        emptyFragment.setArguments(bundle);
        return emptyFragment;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        final boolean showMessage = getArguments().getBoolean(SHOW_MESSAGE);
        final View view = inflater.inflate(R.layout.fragment_empty, container, false);

        final ComposeView composeView = view.findViewById(R.id.empty_state_view);
        EmptyStateUtil.setEmptyStateComposable(composeView);
        composeView.setVisibility(showMessage ? View.VISIBLE : View.GONE);
        return view;
    }
}
