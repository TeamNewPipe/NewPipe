package org.schabi.newpipe.history;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import icepick.State;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public abstract class HistoryFragment<E> extends BaseFragment
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

    private Subscription historySubscription;

    protected HistoryRecordManager historyRecordManager;
    protected CompositeDisposable disposables;

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

        historyRecordManager = new HistoryRecordManager(getContext());
        disposables = new CompositeDisposable();
    }

    @NonNull
    protected abstract HistoryEntryAdapter<E, ? extends RecyclerView.ViewHolder> createAdapter();

    protected abstract Single<List<Long>> insert(final Collection<E> entries);

    protected abstract Single<Integer> delete(final Collection<E> entries);

    @NonNull
    protected abstract Flowable<List<E>> getAll();

    @Override
    public void onResume() {
        super.onResume();

        getAll().observeOn(AndroidSchedulers.mainThread()).subscribe(getHistorySubscriber());

        final boolean newEnabled = isHistoryEnabled();
        if (newEnabled != mHistoryIsEnabled) {
            onHistoryIsEnabledChanged(newEnabled);
        }
    }

    @NonNull
    private Subscriber<List<E>> getHistorySubscriber() {
        return new Subscriber<List<E>>() {
            @Override
            public void onSubscribe(Subscription s) {
                if (historySubscription != null) historySubscription.cancel();

                historySubscription = s;
                historySubscription.request(1);
            }

            @Override
            public void onNext(List<E> entries) {
                if (!entries.isEmpty()) {
                    mHistoryAdapter.setEntries(entries);
                    animateView(mEmptyHistoryView, false, 200);

                    if (mRecyclerViewState != null) {
                        mRecyclerView.getLayoutManager().onRestoreInstanceState(mRecyclerViewState);
                        mRecyclerViewState = null;
                    }
                } else {
                    mHistoryAdapter.clear();
                    showEmptyHistory();
                }

                if (historySubscription != null) historySubscription.request(1);
            }

            @Override
            public void onError(Throwable t) {

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
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete_all)
                .setMessage(R.string.delete_all_history_prompt)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_all, (dialog, i) -> clearHistory())
                .show();
    }

    protected void makeSnackbar(@StringRes final int text) {
        if (getActivity() == null) return;

        View view = getActivity().findViewById(R.id.main_content);
        if (view == null) view = mRecyclerView.getRootView();
        Snackbar.make(view, text, Snackbar.LENGTH_LONG).show();
    }

    private void clearHistory() {
        final Collection<E> itemsToDelete = new ArrayList<>(mHistoryAdapter.getItems());

        final Disposable deletion = delete(itemsToDelete)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> Log.d(TAG, "Clear history deleted [" +
                        itemsToDelete.size() + "] items."),
                        error -> Log.e(TAG, "Clear history delete step failed", error)
                );

        final Disposable cleanUp = historyRecordManager.removeOrphanedRecords()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> Log.d(TAG, "Clear history deleted orphaned stream records"),
                        error -> Log.e(TAG, "Clear history remove orphaned records failed", error)
                );

        disposables.addAll(deletion, cleanUp);

        makeSnackbar(R.string.history_cleared);
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
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_history, container, false);
        mRecyclerView = rootView.findViewById(R.id.history_view);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);

        mHistoryAdapter = createAdapter();
        mHistoryAdapter.setOnHistoryItemClickListener(this);
        mRecyclerView.setAdapter(mHistoryAdapter);
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

        if (disposables != null) disposables.dispose();
        if (historySubscription != null) historySubscription.cancel();

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mHistoryIsEnabledChangeListener);
        mSharedPreferences = null;
        mHistoryIsEnabledChangeListener = null;
        mHistoryIsEnabledKey = null;
        historySubscription = null;
        disposables = null;
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

    private class HistoryIsEnabledChangeListener
            implements SharedPreferences.OnSharedPreferenceChangeListener {
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
