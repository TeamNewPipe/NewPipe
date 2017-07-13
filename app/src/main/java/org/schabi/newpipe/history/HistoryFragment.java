package org.schabi.newpipe.history;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.history.dao.HistoryDAO;
import org.schabi.newpipe.history.model.HistoryEntry;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public abstract class HistoryFragment<E extends HistoryEntry> extends Fragment
        implements HistoryEntryAdapter.OnHistoryItemClickListener<E> {

    private boolean mHistoryIsEnabled;
    private HistoryIsEnabledChangeListener mHistoryIsEnabledChangeListener;
    private String mHistoryIsEnabledKey;
    private SharedPreferences mSharedPreferences;
    private RecyclerView mRecyclerView;
    private View mDisabledView;
    private HistoryDAO<E> mHistoryDataSource;
    private HistoryEntryAdapter<E, ? extends RecyclerView.ViewHolder> mHistoryAdapter;
    private View mEmptyHistoryView;
    private ItemTouchHelper.SimpleCallback mHistoryItemSwipeCallback;


    @StringRes
    abstract int getEnabledConfigKey();

    @CallSuper
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHistoryIsEnabledKey = getString(getEnabledConfigKey());

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        // Read history enabled from preferences
        mHistoryIsEnabled = isHistoryEnabled();
        // Register history enabled listener
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mHistoryIsEnabledChangeListener);

        mHistoryDataSource = createHistoryDAO(getContext());

        mHistoryItemSwipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                if(mHistoryAdapter != null) {
                    E historyEntry = mHistoryAdapter.removeItemAt(viewHolder.getAdapterPosition());
                    mHistoryDataSource.removeHistoryEntry(historyEntry);
                }
            }
        };

    }

    @NonNull
    protected abstract HistoryEntryAdapter<E, ? extends RecyclerView.ViewHolder> createAdapter();

    @Override
    public void onResume() {
        super.onResume();
        onHistoryEntriesLoaded(mHistoryDataSource.loadAllHistoryEntries());

        boolean newEnabled = isHistoryEnabled();
        if(newEnabled != mHistoryIsEnabled) {
            onHistoryIsEnabledChanged(newEnabled);
        }
    }

    private boolean isHistoryEnabled() {
        return mSharedPreferences.getBoolean(mHistoryIsEnabledKey, false);
    }

    private void onEmptyHistory() {
        if (mHistoryIsEnabled) {
            animateView(mEmptyHistoryView, true, 200);
        }
    }

    public void onHistoryEntriesLoaded(@NonNull E[] historyEntries) {
        if (historyEntries.length > 0) {
            mHistoryAdapter.setEntries(historyEntries);
            animateView(mEmptyHistoryView, false, 200);
        } else {
            mHistoryAdapter.clear();
            onEmptyHistory();
        }
    }


    @Nullable
    @CallSuper
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.history_view);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);

        mHistoryAdapter = createAdapter();
        mHistoryAdapter.setOnHistoryItemClickListener(this);
        mRecyclerView.setAdapter(mHistoryAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(mHistoryItemSwipeCallback);
        itemTouchHelper.attachToRecyclerView(mRecyclerView);
        mDisabledView = rootView.findViewById(R.id.history_disabled_view);
        mEmptyHistoryView = rootView.findViewById(R.id.history_empty);

        if (mHistoryIsEnabled) {
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mDisabledView.setVisibility(View.VISIBLE);
        }

        return rootView;
    }

    @CallSuper
    @Override
    public void onDestroy() {
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mHistoryIsEnabledChangeListener);
        mSharedPreferences = null;
        mHistoryIsEnabledChangeListener = null;
        mHistoryIsEnabledKey = null;
        mHistoryDataSource = null;
    }

    /**
     * Called when the history is cleared
     */
    @CallSuper
    public void onClearHistory() {
        mHistoryDataSource.clearHistory();
        mHistoryAdapter.clear();
        onEmptyHistory();
    }

    /**
     * Called when history enabled flag is changed.
     *
     * @param historyIsEnabled the new value
     */
    @CallSuper
    public void onHistoryIsEnabledChanged(boolean historyIsEnabled) {
        mHistoryIsEnabled = historyIsEnabled;
        if (historyIsEnabled) {
            animateView(mRecyclerView, true, 300);
            animateView(mDisabledView, false, 300);
            if(mHistoryAdapter.isEmpty()) {
                animateView(mEmptyHistoryView, true, 300);
            }
        } else {
            animateView(mRecyclerView, false, 300);
            animateView(mDisabledView, true, 300);
            animateView(mEmptyHistoryView, false, 300);
        }
    }

    /**
     * Creates a new history DAO
     *
     * @param context the fragments context
     * @return the history DAO
     */
    @NonNull
    protected abstract HistoryDAO<E> createHistoryDAO(Context context);

    private class HistoryIsEnabledChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(mHistoryIsEnabledKey)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (mHistoryIsEnabled != enabled) {
                    onHistoryIsEnabledChanged(enabled);
                }
            }
        }
    }
}
