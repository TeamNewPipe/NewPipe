package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.InfoCache;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class HistorySettingsFragment extends BasePreferenceFragment {
    private String cacheWipeKey;
    private String viewsHistoryClearKey;
    private String playbackStatesClearKey;
    private String searchHistoryClearKey;
    private HistoryRecordManager recordManager;
    private CompositeDisposable disposables;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cacheWipeKey = getString(R.string.metadata_cache_wipe_key);
        viewsHistoryClearKey = getString(R.string.clear_views_history_key);
        playbackStatesClearKey = getString(R.string.clear_playback_states_key);
        searchHistoryClearKey = getString(R.string.clear_search_history_key);
        recordManager = new HistoryRecordManager(getActivity());
        disposables = new CompositeDisposable();
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.history_settings);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(cacheWipeKey)) {
            InfoCache.getInstance().clearCache();
            Toast.makeText(preference.getContext(), R.string.metadata_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        if (preference.getKey().equals(viewsHistoryClearKey)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.delete_view_history_alert)
                    .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                    .setPositiveButton(R.string.delete, ((dialog, which) -> {
                        final Disposable onDeletePlaybackStates
                                = recordManager.deleteCompelteStreamStateHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        howManyDeleted -> Toast.makeText(getActivity(),
                                                R.string.watch_history_states_deleted,
                                                Toast.LENGTH_SHORT).show(),
                                        throwable -> ErrorActivity.reportError(getContext(),
                                                throwable,
                                                SettingsActivity.class, null,
                                                ErrorActivity.ErrorInfo.make(
                                                        UserAction.DELETE_FROM_HISTORY,
                                                        "none",
                                                        "Delete playback states",
                                                        R.string.general_error)));

                        final Disposable onDelete = recordManager.deleteWholeStreamHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        howManyDeleted -> Toast.makeText(getActivity(),
                                                R.string.watch_history_deleted,
                                                Toast.LENGTH_SHORT).show(),
                                        throwable -> ErrorActivity.reportError(getContext(),
                                                throwable,
                                                SettingsActivity.class, null,
                                                ErrorActivity.ErrorInfo.make(
                                                        UserAction.DELETE_FROM_HISTORY,
                                                        "none",
                                                        "Delete view history",
                                                        R.string.general_error)));

                        final Disposable onClearOrphans = recordManager.removeOrphanedRecords()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        howManyDeleted -> {
                                        },
                                        throwable -> ErrorActivity.reportError(getContext(),
                                                throwable,
                                                SettingsActivity.class, null,
                                                ErrorActivity.ErrorInfo.make(
                                                        UserAction.DELETE_FROM_HISTORY,
                                                        "none",
                                                        "Delete search history",
                                                        R.string.general_error)));
                        disposables.add(onDeletePlaybackStates);
                        disposables.add(onClearOrphans);
                        disposables.add(onDelete);
                    }))
                    .create()
                    .show();
        }

        if (preference.getKey().equals(playbackStatesClearKey)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.delete_playback_states_alert)
                    .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                    .setPositiveButton(R.string.delete, ((dialog, which) -> {

                        final Disposable onDeletePlaybackStates
                                = recordManager.deleteCompelteStreamStateHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        howManyDeleted -> Toast.makeText(getActivity(),
                                                R.string.watch_history_states_deleted,
                                                Toast.LENGTH_SHORT).show(),
                                        throwable -> ErrorActivity.reportError(getContext(),
                                                throwable,
                                                SettingsActivity.class, null,
                                                ErrorActivity.ErrorInfo.make(
                                                        UserAction.DELETE_FROM_HISTORY,
                                                        "none",
                                                        "Delete playback states",
                                                        R.string.general_error)));

                        disposables.add(onDeletePlaybackStates);
                    }))
                    .create()
                    .show();
        }

        if (preference.getKey().equals(searchHistoryClearKey)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.delete_search_history_alert)
                    .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                    .setPositiveButton(R.string.delete, ((dialog, which) -> {
                        final Disposable onDelete = recordManager.deleteCompleteSearchHistory()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        howManyDeleted -> Toast.makeText(getActivity(),
                                                R.string.search_history_deleted,
                                                Toast.LENGTH_SHORT).show(),
                                        throwable -> ErrorActivity.reportError(getContext(),
                                                throwable,
                                                SettingsActivity.class, null,
                                                ErrorActivity.ErrorInfo.make(
                                                        UserAction.DELETE_FROM_HISTORY,
                                                        "none",
                                                        "Delete search history",
                                                        R.string.general_error)));
                        disposables.add(onDelete);
                    }))
                    .create()
                    .show();
        }

        return super.onPreferenceTreeClick(preference);
    }
}
