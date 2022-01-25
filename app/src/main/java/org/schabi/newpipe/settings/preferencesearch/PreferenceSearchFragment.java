package org.schabi.newpipe.settings.preferencesearch;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.schabi.newpipe.databinding.SettingsPreferencesearchFragmentBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the search results.
 */
public class PreferenceSearchFragment extends Fragment {
    public static final String NAME = PreferenceSearchFragment.class.getSimpleName();

    private PreferenceSearcher searcher;

    private SettingsPreferencesearchFragmentBinding binding;
    private PreferenceSearchAdapter adapter;

    public void setSearcher(final PreferenceSearcher searcher) {
        this.searcher = searcher;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = SettingsPreferencesearchFragmentBinding.inflate(inflater, container, false);

        binding.searchResults.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new PreferenceSearchAdapter();
        adapter.setOnItemClickListener(this::onItemClicked);
        binding.searchResults.setAdapter(adapter);

        return binding.getRoot();
    }

    public void updateSearchResults(final String keyword) {
        if (adapter == null || searcher == null) {
            return;
        }

        final List<PreferenceSearchItem> results =
                !TextUtils.isEmpty(keyword)
                    ? searcher.searchFor(keyword)
                    : new ArrayList<>();

        adapter.setContent(new ArrayList<>(results));

        setEmptyViewShown(results.isEmpty());
    }

    private void setEmptyViewShown(final boolean shown) {
        binding.emptyStateView.setVisibility(shown ? View.VISIBLE : View.GONE);
        binding.searchResults.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    public void onItemClicked(final PreferenceSearchItem item) {
        if (!(getActivity() instanceof PreferenceSearchResultListener)) {
            throw new ClassCastException(
                getActivity().toString() + " must implement SearchPreferenceResultListener");
        }

        ((PreferenceSearchResultListener) getActivity()).onSearchResultClicked(item);
    }
}
