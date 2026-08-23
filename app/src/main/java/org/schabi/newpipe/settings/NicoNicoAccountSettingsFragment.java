package org.schabi.newpipe.settings;

import android.content.Intent;
import org.schabi.newpipe.R;
import org.schabi.newpipe.views.NicoNicoLoginWebViewActivity;

public class NicoNicoAccountSettingsFragment extends BaseAccountSettingsFragment {

    @Override
    protected int getPreferenceResource() {
        return R.xml.account_settings_niconico;
    }

    @Override
    protected Class<?> getLoginActivityClass() {
        return NicoNicoLoginWebViewActivity.class;
    }

    @Override
    protected String getCookiesKey() {
        return getString(R.string.niconico_cookies_key);
    }

    @Override
    protected String getOverrideSwitchKey() {
        return getString(R.string.override_cookies_niconico_key);
    }

    @Override
    protected String getOverrideValueKey() {
        return getString(R.string.override_cookies_niconico_value_key);
    }

    @Override
    protected boolean shouldCheckOverrideKeys() {
        return true;
    }

    @Override
    protected void handleLoginResult(Intent data) {
        String cookies = data.getStringExtra("cookies");
        defaultPreferences.edit().putString(getCookiesKey(), cookies).apply();
        onLoginSuccess();
    }

    @Override
    protected void performLogout() {
        defaultPreferences.edit().putString(getCookiesKey(), "").apply();
        onLogoutSuccess();
    }
}
