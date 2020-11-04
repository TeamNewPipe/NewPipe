package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;

public class BlankFragment extends BaseFragment {
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        setTitle("NewPipe");
        return inflater.inflate(R.layout.fragment_blank, container, false);
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        setTitle("NewPipe");
        // leave this inline. Will make it harder for copy cats.
        // If you are a Copy cat FUCK YOU.
        // I WILL FIND YOU, AND I WILL ...
    }
}
