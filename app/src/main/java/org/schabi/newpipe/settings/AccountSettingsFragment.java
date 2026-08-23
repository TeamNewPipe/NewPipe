package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;
import androidx.preference.Preference;
import org.schabi.newpipe.R;

public class AccountSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.account_settings);

        final Preference clearWebViewCookies = findPreference("clear_webview_cookies");
        if (clearWebViewCookies != null) {
            clearWebViewCookies.setOnPreferenceClickListener(preference -> {
                clearWebViewCookies();
                return true;
            });
        }
    }

    private void clearWebViewCookies() {
        CookieSyncManager.createInstance(requireContext());
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(success -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.webview_cookies_cleared, Toast.LENGTH_SHORT).show();
            }
        });
        cookieManager.flush();
    }
}
