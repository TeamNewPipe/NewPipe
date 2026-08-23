package org.schabi.newpipe.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.Preference;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ServiceHelper;

import static android.app.Activity.RESULT_OK;

public abstract class BaseAccountSettingsFragment extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    protected static final int REQUEST_LOGIN = 1;

    protected Preference login;
    protected Preference logout;
    protected Preference overrideSwitch;
    protected Preference overrideValue;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(getPreferenceResource());
        initializePreferences();
        setupClickListeners();
        updateLoginLogoutState();
        configureOverridePreferences();
    }

    protected abstract int getPreferenceResource();
    protected abstract Class<?> getLoginActivityClass();
    protected abstract String getCookiesKey();
    protected abstract String getOverrideSwitchKey();
    protected abstract String getOverrideValueKey();
    protected abstract boolean shouldCheckOverrideKeys();
    protected abstract void handleLoginResult(Intent data);
    protected abstract void performLogout();

    protected void onLoginClicked() {
        startLoginActivity();
    }

    protected final void startLoginActivity() {
        Intent intent = new Intent(getContext(), getLoginActivityClass());
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void initializePreferences() {
        login = findPreference(getString(R.string.login_key));
        logout = findPreference(getString(R.string.logout_key));

        if (shouldCheckOverrideKeys()) {
            overrideSwitch = findPreference(getOverrideSwitchKey());
            overrideValue = findPreference(getOverrideValueKey());
        }
    }

    private void setupClickListeners() {
        login.setOnPreferenceClickListener(preference -> {
            onLoginClicked();
            return true;
        });

        logout.setOnPreferenceClickListener(preference -> {
            performLogout();
            return true;
        });

        if (shouldCheckOverrideKeys()) {
            setupOverrideClickListeners();
        }
    }

    private void setupOverrideClickListeners() {
        if (overrideSwitch != null) {
            overrideSwitch.setOnPreferenceClickListener(preference -> {
                ServiceHelper.initServices(getContext());
                return true;
            });
        }

        if (overrideValue != null) {
            overrideValue.setOnPreferenceClickListener(preference -> {
                ServiceHelper.initServices(getContext());
                return true;
            });
        }
    }

    private void updateLoginLogoutState() {
        boolean hasCredentials = !defaultPreferences.getString(getCookiesKey(), "").equals("");
        login.setEnabled(!hasCredentials);
        logout.setEnabled(hasCredentials);
    }

    private void configureOverridePreferences() {
        if (shouldCheckOverrideKeys() && overrideValue != null) {
            overrideValue.setEnabled(defaultPreferences.getBoolean(getOverrideSwitchKey(), false));
        }
    }

    protected void onLoginSuccess() {
        refreshAccountDependentState();
        Toast.makeText(requireContext(), R.string.success, Toast.LENGTH_SHORT).show();
        login.setEnabled(false);
        logout.setEnabled(true);
    }

    protected void onLogoutSuccess() {
        refreshAccountDependentState();
        Toast.makeText(requireContext(), R.string.success, Toast.LENGTH_SHORT).show();
        login.setEnabled(true);
        logout.setEnabled(false);
    }

    protected void refreshAccountDependentState() {
        ServiceHelper.initServices(getContext());
        InfoCache.getInstance().clearCache();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getCookiesKey())) {
            refreshAccountDependentState();
            updateLoginLogoutState();
            return;
        }
        if (shouldCheckOverrideKeys() &&
                (key.equals(getOverrideSwitchKey()) || key.equals(getOverrideValueKey()))) {
            refreshAccountDependentState();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) {
            handleLoginResult(data);
        }
    }
}
