package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.InfoCache;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class HistorySettingsFragment extends BasePreferenceFragment {
    private String cacheWipeKey;
    private String viewsHistoryClearKey;
    private String playbackStatesClearKey;
    private String searchHistoryClearKey;
    private HistoryRecordManager recordManager;
    private CompositeDisposable disposables;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        cacheWipeKey = getString(R.string.metadata_cache_wipe_key);
        viewsHistoryClearKey = getString(R.string.clear_views_history_key);
        playbackStatesClearKey = getString(R.string.clear_playback_states_key);
        searchHistoryClearKey = getString(R.string.clear_search_history_key);
        recordManager = new HistoryRecordManager(getActivity());
        disposables = new CompositeDisposable();

        final Preference clearCookiePref = requirePreference(R.string.clear_cookie_key);
        clearCookiePref.setOnPreferenceClickListener(preference -> {
            defaultPreferences.edit()
                    .putString(getString(R.string.recaptcha_cookies_key), "").apply();
            DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "");
            Toast.makeText(getActivity(), R.string.recaptcha_cookies_cleared,
                    Toast.LENGTH_SHORT).show();
            clearCookiePref.setEnabled(false);
            return true;
        });

        if (defaultPreferences.getString(getString(R.string.recaptcha_cookies_key), "").isEmpty()) {
            clearCookiePref.setEnabled(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(cacheWipeKey)) {
            InfoCache.getInstance().clearCache();
            Toast.makeText(requireContext(),
                    R.string.metadata_cache_wipe_complete_notice, Toast.LENGTH_SHORT).show();
        } else if (preference.getKey().equals(viewsHistoryClearKey)) {
            openDeleteWatchHistoryDialog(requireContext(), recordManager, disposables);
        } else if (preference.getKey().equals(playbackStatesClearKey)) {
            openDeletePlaybackStatesDialog(requireContext(), recordManager, disposables);
        } else if (preference.getKey().equals(searchHistoryClearKey)) {
            openDeleteSearchHistoryDialog(requireContext(), recordManager, disposables);
        } else {
            return super.onPreferenceTreeClick(preference);
        }
        return true;
    }

    private static Disposable getDeletePlaybackStatesDisposable(
            @NonNull final Context context, final HistoryRecordManager recordManager) {
        return recordManager.deleteCompleteStreamStateHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> Toast.makeText(context,
                                R.string.watch_history_states_deleted,  Toast.LENGTH_SHORT).show(),
                        throwable -> ErrorUtil.openActivity(context,
                                new ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY,
                                        "Delete playback states")));
    }

    private static Disposable getWholeStreamHistoryDisposable(
            @NonNull final Context context, final HistoryRecordManager recordManager) {
        return recordManager.deleteWholeStreamHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> Toast.makeText(context,
                                R.string.watch_history_deleted, Toast.LENGTH_SHORT).show(),
                        throwable -> ErrorUtil.openActivity(context,
                                new ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY,
                                        "Delete from history")));
    }

    private static Disposable getRemoveOrphanedRecordsDisposable(
            @NonNull final Context context, final HistoryRecordManager recordManager) {
        return recordManager.removeOrphanedRecords()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> { },
                        throwable -> ErrorUtil.openActivity(context,
                                new ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY,
                                        "Clear orphaned records")));
    }

    private static Disposable getDeleteSearchHistoryDisposable(
            @NonNull final Context context, final HistoryRecordManager recordManager) {
        return recordManager.deleteCompleteSearchHistory()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> Toast.makeText(context,
                                R.string.search_history_deleted, Toast.LENGTH_SHORT).show(),
                        throwable -> ErrorUtil.openActivity(context,
                                new ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY,
                                        "Delete search history")));
    }

    public static void openDeleteWatchHistoryDialog(@NonNull final Context context,
                                                    final HistoryRecordManager recordManager,
                                                    final CompositeDisposable disposables) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_view_history_alert)
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                .setPositiveButton(R.string.delete, ((dialog, which) -> {
                    disposables.add(getDeletePlaybackStatesDisposable(context, recordManager));
                    disposables.add(getWholeStreamHistoryDisposable(context, recordManager));
                    disposables.add(getRemoveOrphanedRecordsDisposable(context, recordManager));
                }))
                .show();
    }

    public static void openDeletePlaybackStatesDialog(@NonNull final Context context,
                                                      final HistoryRecordManager recordManager,
                                                      final CompositeDisposable disposables) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_playback_states_alert)
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                .setPositiveButton(R.string.delete, ((dialog, which) ->
                        disposables.add(getDeletePlaybackStatesDisposable(context, recordManager))))
                .show();
    }

    public static void openDeleteSearchHistoryDialog(@NonNull final Context context,
                                                     final HistoryRecordManager recordManager,
                                                     final CompositeDisposable disposables) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_search_history_alert)
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                .setPositiveButton(R.string.delete, ((dialog, which) ->
                        disposables.add(getDeleteSearchHistoryDisposable(context, recordManager))))
                .show();
    }
}
