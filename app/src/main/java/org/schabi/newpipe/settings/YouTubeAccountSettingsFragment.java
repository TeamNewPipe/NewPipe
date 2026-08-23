package org.schabi.newpipe.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.views.YouTubeLoginWebViewActivity;
import org.schabi.newpipe.youtube.LocalDomPoTokenProvider;

public class YouTubeAccountSettingsFragment extends BaseAccountSettingsFragment {

    @Override
    protected int getPreferenceResource() {
        return R.xml.account_settings_youtube;
    }

    @Override
    protected Class<?> getLoginActivityClass() {
        return YouTubeLoginWebViewActivity.class;
    }

    @Override
    protected void onLoginClicked() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.youtube_login_warning_title)
                .setMessage(R.string.youtube_login_warning_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> startLoginActivity())
                .show();
    }

    @Override
    protected String getCookiesKey() {
        return getString(R.string.youtube_cookies_key);
    }

    @Override
    protected String getOverrideSwitchKey() {
        return getString(R.string.override_cookies_youtube_key);
    }

    @Override
    protected String getOverrideValueKey() {
        return getString(R.string.override_cookies_youtube_value_key);
    }

    @Override
    protected boolean shouldCheckOverrideKeys() {
        return false; // YouTube doesn't use override preferences
    }

    @Override
    protected void handleLoginResult(Intent data) {
        String cookies = data.getStringExtra("cookies");
        String pot = data.getStringExtra("pot");

        try {
            YoutubeParsingHelper.getAuthorizationHeader(cookies);
            defaultPreferences.edit()
                    .putString(getCookiesKey(), cookies)
                    .putString(getString(R.string.youtube_po_token_key), pot)
                    .apply();
            LocalDomPoTokenProvider.INSTANCE.invalidate();
            onLoginSuccess();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.try_again, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void performLogout() {
        defaultPreferences.edit()
                .putString(getCookiesKey(), "")
                .putString(getString(R.string.youtube_po_token_key), "")
                .apply();
        LocalDomPoTokenProvider.INSTANCE.invalidate();
        onLogoutSuccess();
    }

    @Override
    protected void refreshAccountDependentState() {
        super.refreshAccountDependentState();
        App.reconcileYoutubePlayerClient(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
