package org.schabi.newpipe.frost;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

/**
 * Library hub: one glass page that leads to Downloads, History and Playlists.
 */
public class LibraryHubFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_library_hub, container, false);

        root.findViewById(R.id.hub_downloads).setOnClickListener(v ->
                NavigationHelper.openDownloads(requireActivity()));
        root.findViewById(R.id.hub_history).setOnClickListener(v ->
                NavigationHelper.openStatisticFragment(getParentFragmentManager()));
        root.findViewById(R.id.hub_playlists).setOnClickListener(v ->
                NavigationHelper.openBookmarksFragment(getParentFragmentManager()));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.rail_library);
        }
    }
}
