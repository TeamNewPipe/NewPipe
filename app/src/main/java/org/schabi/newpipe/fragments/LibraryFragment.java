/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentLibraryBinding;
import org.schabi.newpipe.databinding.YpLibraryRowBinding;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.util.NavigationHelper;

/**
 * YewPlay: the "Library" destination of the bottom bar. It gathers what belongs to the user:
 * history, downloads, playlists, settings and the about page.
 */
public class LibraryFragment extends BaseFragment {
    private static final String PLAYLISTS_TAG = "yp_library_playlists";

    @Nullable
    private FragmentLibraryBinding binding;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding = FragmentLibraryBinding.bind(rootView);

        setupRow(binding.ypLibraryHistory, R.drawable.ic_history, R.string.yp_library_history,
                v -> NavigationHelper.openStatisticFragment(getFM()));
        setupRow(binding.ypLibraryDownloads, R.drawable.ic_file_download,
                R.string.yp_library_downloads, v -> NavigationHelper.openDownloads(activity));
        setupRow(binding.ypLibraryAbout, R.drawable.ic_info_outline, R.string.yp_library_about,
                v -> NavigationHelper.openAbout(requireContext()));

        if (getChildFragmentManager().findFragmentByTag(PLAYLISTS_TAG) == null) {
            final BookmarkFragment playlists = new BookmarkFragment();
            playlists.useAsFrontPage(true);
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.yp_library_playlists, playlists, PLAYLISTS_TAG)
                    .commit();
        }
    }

    private static void setupRow(@NonNull final YpLibraryRowBinding row,
                                 @DrawableRes final int icon,
                                 @StringRes final int title,
                                 @NonNull final View.OnClickListener listener) {
        row.ypRowIcon.setImageResource(icon);
        row.ypRowTitle.setText(title);
        row.getRoot().setOnClickListener(listener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            final boolean historyEnabled = PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getBoolean(getString(R.string.enable_watch_history_key), true);
            binding.ypLibraryHistory.getRoot()
                    .setVisibility(historyEnabled ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_yp_library, menu);
        setTitle(getString(R.string.yp_nav_library));
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setSubtitle(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_yp_settings) {
            NavigationHelper.openSettings(requireContext());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
