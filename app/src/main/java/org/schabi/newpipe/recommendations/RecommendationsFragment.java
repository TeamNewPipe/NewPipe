package org.schabi.newpipe.recommendations;

import android.os.Bundle;
import android.content.SharedPreferences;
import org.schabi.newpipe.local.history.IncognitoMode;
import org.schabi.newpipe.profiles.ProfileManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.List;

/** A home tab backed exclusively by the local candidate pool. */
public class RecommendationsFragment extends BaseListFragment<List<StreamInfoItem>, Void> {
    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.KIOSKS;
    }

    @Override
    protected boolean showRecommendationReason() {
        return true;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        startLoading(false);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences preferences, final String key) {
        super.onSharedPreferenceChanged(preferences, key);
        if (getView() != null && (IncognitoMode.KEY.equals(key)
                || ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY.equals(key))) {
            startLoading(false);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(R.string.local_recommendations_title).setOnMenuItemClickListener(item -> {
            RecommendationControls.show(requireContext(), () -> startLoading(false));
            return true;
        });
    }

    @Override
    protected void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        infoListAdapter.clearStreamItemList();
        handleResult(LocalRecommendations.feed(requireContext(),
                ServiceHelper.getSelectedServiceId(requireContext())));
    }

    @Override
    public void handleResult(final List<StreamInfoItem> result) {
        super.handleResult(result);
        isLoading.set(false);
        setTitle(getString(R.string.local_recommendations_feed));
        infoListAdapter.addInfoItemList(result);
        showListFooter(false);
        if (result.isEmpty()) {
            showEmptyState();
            setEmptyStateMessage(R.string.local_recommendations_empty);
        }
    }

    @Override
    protected void loadMoreItems() { }

    @Override
    protected boolean hasMoreItems() {
        return false;
    }

    @Override
    public void handleNextItems(final Void ignored) { }
}
