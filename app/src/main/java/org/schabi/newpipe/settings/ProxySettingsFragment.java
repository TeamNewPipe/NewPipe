package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

/**
 * A fragment that displays proxy settings.
 */
public class ProxySettingsFragment extends BasePreferenceFragment {

    private boolean preferencesChanged = false;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        //addPreferencesFromResource(R.xml.proxy_settings);
        addPreferencesFromResourceRegistry();
        preferenceChangeListener = (sharedPreferences, key) -> {
            preferencesChanged = true;
        };
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (preferencesChanged && getActivity() != null && !getActivity().isFinishing()) {
            showRestartDialog();
        }
    }

    private void showRestartDialog() {
        // Show Alert Dialogue
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.restart_app_message);
        builder.setTitle(R.string.restart_app_title);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            // Restarts the app
            if (activity == null) {
                return;
            }
            NavigationHelper.restartApp(activity);
        });
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
        });
        final android.app.AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (preferenceChangeListener != null && getPreferenceScreen() != null) {
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
    }
}
