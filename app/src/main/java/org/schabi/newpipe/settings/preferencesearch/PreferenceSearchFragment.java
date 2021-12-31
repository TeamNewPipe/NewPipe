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
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Displays the search results.
 */
public class PreferenceSearchFragment extends Fragment {
    public static final String NAME = PreferenceSearchFragment.class.getSimpleName();

    private PreferenceSearcher searcher;

    private SearchViewHolder viewHolder;
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
        final View rootView =
                inflater.inflate(R.layout.settings_preferencesearch_fragment, container, false);

        viewHolder = new SearchViewHolder(rootView);
        viewHolder.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new PreferenceSearchAdapter();
        adapter.setOnItemClickListener(this::onItemClicked);
        viewHolder.recyclerView.setAdapter(adapter);

        return rootView;
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
        viewHolder.emptyStateView.setVisibility(shown ? View.VISIBLE : View.GONE);
        viewHolder.recyclerView.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    public void onItemClicked(final PreferenceSearchItem item) {
        if (!(getActivity() instanceof PreferenceSearchResultListener)) {
            throw new ClassCastException(
                getActivity().toString() + " must implement SearchPreferenceResultListener");
        }

        ((PreferenceSearchResultListener) getActivity()).onSearchResultClicked(item);
    }

    private static class SearchViewHolder {
        private final RecyclerView recyclerView;
        private final View emptyStateView;

        SearchViewHolder(final View root) {
            recyclerView = Objects.requireNonNull(root.findViewById(R.id.list));
            emptyStateView = Objects.requireNonNull(root.findViewById(R.id.empty_state_view));
        }
    }
}
