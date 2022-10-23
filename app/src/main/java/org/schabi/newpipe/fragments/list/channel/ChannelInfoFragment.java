package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.databinding.FragmentChannelInfoBinding;

public class ChannelInfoFragment extends BaseFragment {
    private String description;

    public static ChannelInfoFragment getInstance(final String description) {
        final ChannelInfoFragment fragment = new ChannelInfoFragment();
        fragment.description = description;
        return fragment;
    }

    public ChannelInfoFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        final FragmentChannelInfoBinding binding =
                FragmentChannelInfoBinding.inflate(inflater, container, false);
        binding.descriptionText.setText(description);

        return binding.getRoot();
    }

}
