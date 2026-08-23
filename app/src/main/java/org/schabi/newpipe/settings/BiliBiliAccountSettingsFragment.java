package org.schabi.newpipe.settings;

import android.content.Intent;
import org.schabi.newpipe.R;
import org.schabi.newpipe.views.BiliBiliLoginWebViewActivity;

public class BiliBiliAccountSettingsFragment extends BaseAccountSettingsFragment {

    @Override
    protected int getPreferenceResource() {
        return R.xml.account_settings_bilibili;
    }

    @Override
    protected Class<?> getLoginActivityClass() {
        return BiliBiliLoginWebViewActivity.class;
    }

    @Override
    protected String getCookiesKey() {
        return getString(R.string.bilibili_cookies_key);
    }

    @Override
    protected String getOverrideSwitchKey() {
        return getString(R.string.override_cookies_bilibili_key);
    }

    @Override
    protected String getOverrideValueKey() {
        return getString(R.string.override_cookies_bilibili_value_key);
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
