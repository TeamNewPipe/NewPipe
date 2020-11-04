package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;

public class EmptyFragment extends BaseFragment {
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_empty, container, false);
    }
}
