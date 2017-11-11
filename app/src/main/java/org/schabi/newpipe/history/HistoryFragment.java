package org.schabi.newpipe.history;


import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.dao.HistoryDAO;
import org.schabi.newpipe.database.history.model.HistoryEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import icepick.State;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public abstract class HistoryFragment<E extends HistoryEntry> extends BaseFragment
        implements HistoryEntryAdapter.OnHistoryItemClickListener<E> {

    private SharedPreferences mSharedPreferences;
    private String mHistoryIsEnabledKey;
    private boolean mHistoryIsEnabled;
    private HistoryIsEnabledChangeListener mHistoryIsEnabledChangeListener;

    private View mDisabledView;
    private View mEmptyHistoryView;

    @State
    Parcelable mRecyclerViewState;
    private RecyclerView mRecyclerView;
    private HistoryEntryAdapter<E, ? extends RecyclerView.ViewHolder> mHistoryAdapter;
    private ItemTouchHelper.SimpleCallback mHistoryItemSwipeCallback;
    // private int allowedSwipeToDeleteDirections = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;

    private HistoryDAO<E> mHistoryDataSource;
    private PublishSubject<Collection<E>> mHistoryEntryDeleteSubject;
    private PublishSubject<Collection<E>> mHistoryEntryInsertSubject;

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

        mHistoryDataSource = createHistoryDAO();

        mHistoryEntryDeleteSubject = PublishSubject.create();
        mHistoryEntryDeleteSubject
                .observeOn(Schedulers.io())
                .subscribe(new Consumer<Collection<E>>() {
                    @Override
                    public void accept(Collection<E> historyEntries) throws Exception {
                        mHistoryDataSource.delete(historyEntries);
                    }
                });

        mHistoryEntryInsertSubject = PublishSubject.create();
        mHistoryEntryInsertSubject
                .observeOn(Schedulers.io())
                .subscribe(new Consumer<Collection<E>>() {
                    @Override
                    public void accept(Collection<E> historyEntries) throws Exception {
                        mHistoryDataSource.insertAll(historyEntries);
                    }
                });


    }

    protected void historyItemSwipeCallback(int swipeDirection) {
        mHistoryItemSwipeCallback = new ItemTouchHelper.SimpleCallback(0, swipeDirection) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                if (mHistoryAdapter != null) {
                    final E historyEntry = mHistoryAdapter.removeItemAt(viewHolder.getAdapterPosition());
                    mHistoryEntryDeleteSubject.onNext(Collections.singletonList(historyEntry));

                    View view = getActivity().findViewById(R.id.main_content);
                    if (view == null) view = mRecyclerView.getRootView();

                    Snackbar.make(view, R.string.item_deleted, 5 * 1000)
                            .setActionTextColor(Color.WHITE)
                            .setAction(R.string.undo, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mHistoryEntryInsertSubject.onNext(Collections.singletonList(historyEntry));
                                }
                            }).show();
                }
            }
        };
    }

    @NonNull
    protected abstract HistoryEntryAdapter<E, ? extends RecyclerView.ViewHolder> createAdapter();

    @Override
    public void onResume() {
        super.onResume();
        mHistoryDataSource.getAll()
                .toObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getHistoryListConsumer());
        boolean newEnabled = isHistoryEnabled();
        if (newEnabled != mHistoryIsEnabled) {
            onHistoryIsEnabledChanged(newEnabled);
        }
    }

    @NonNull
    private Observer<List<E>> getHistoryListConsumer() {
        return new Observer<List<E>>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(@NonNull List<E> historyEntries) {
                if (!historyEntries.isEmpty()) {
                    mHistoryAdapter.setEntries(historyEntries);
                    animateView(mEmptyHistoryView, false, 200);

                    if (mRecyclerViewState != null) {
                        mRecyclerView.getLayoutManager().onRestoreInstanceState(mRecyclerViewState);
                        mRecyclerViewState = null;
                    }
                } else {
                    mHistoryAdapter.clear();
                    showEmptyHistory();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {
                // TODO: error handling like in (see e.g. subscription fragment)
            }

            @Override
            public void onComplete() {

            }
        };
    }

    private boolean isHistoryEnabled() {
        return mSharedPreferences.getBoolean(mHistoryIsEnabledKey, false);
    }

    /**
     * Called when the history is cleared to update the views
     */
    @MainThread
    public void onHistoryCleared() {
        final Parcelable stateBeforeClear = mRecyclerView.getLayoutManager().onSaveInstanceState();
        final Collection<E> itemsToDelete = new ArrayList<>(mHistoryAdapter.getItems());
        mHistoryEntryDeleteSubject.onNext(itemsToDelete);

        View view = getActivity().findViewById(R.id.main_content);
        if (view == null) view = mRecyclerView.getRootView();

        if (!itemsToDelete.isEmpty()) {
            Snackbar.make(view, R.string.history_cleared, 5 * 1000)
                    .setActionTextColor(Color.WHITE)
                    .setAction(R.string.undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mRecyclerViewState = stateBeforeClear;
                            mHistoryEntryInsertSubject.onNext(itemsToDelete);
                        }
                    }).show();
        } else {
            Snackbar.make(view, R.string.history_cleared, Snackbar.LENGTH_LONG).show();
        }

        mHistoryAdapter.clear();
        showEmptyHistory();

    }

    private void showEmptyHistory() {
        if (mHistoryIsEnabled) {
            animateView(mEmptyHistoryView, true, 200);
        }
    }

    @Nullable
    @CallSuper
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);
        mRecyclerView = rootView.findViewById(R.id.history_view);

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
            mRecyclerView.setVisibility(View.GONE);
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

    @Override
    public void onPause() {
        super.onPause();
        mRecyclerViewState = mRecyclerView.getLayoutManager().onSaveInstanceState();
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
            if (mHistoryAdapter.isEmpty()) {
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
     * @return the history DAO
     */
    @NonNull
    protected abstract HistoryDAO<E> createHistoryDAO();

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
