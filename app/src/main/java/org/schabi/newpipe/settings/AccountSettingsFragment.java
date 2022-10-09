package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.SignInActivity;

public class AccountSettingsFragment extends PreferenceFragmentCompat {
    public static final String TAG = "AccountSettingsFragment";
    private ActivityResultLauncher<Intent> activityLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getData() == null) {
                        return;
                    }

                    final String site = result.getData().getStringExtra("site");
                    final String cookies = result.getData().getStringExtra("cookies");

                    setCookies(site, cookies);
                });
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {
        setPreferencesFromResource(R.xml.account_settings, rootKey);

        setupYoutubePreferences();
    }

    private void setupYoutubePreferences() {
        final Preference youTubeSignInPreference =
                findPreference(getString(R.string.youtube_sign_in_settings_key));
        assert youTubeSignInPreference != null;
        youTubeSignInPreference.setOnPreferenceClickListener((Preference p) -> {
            final Intent intent = new Intent(getContext(), SignInActivity.class);
            activityLauncher.launch(intent);
            return true;
        });

        final Preference youTubeIncludeCookiesInSearchPreference =
                findPreference(getString(R.string.youtube_include_cookies_in_search_key));
        assert youTubeIncludeCookiesInSearchPreference != null;
        youTubeIncludeCookiesInSearchPreference
                .setOnPreferenceChangeListener((Preference p, Object newValue) -> {
                    DownloaderImpl.getInstance().updateIncludeCookiesInSearchingSettingWithValue(
                            (boolean) newValue);
                    return true;
                });

        final Preference youTubeClearCookiesPreference =
                findPreference(getString(R.string.youtube_clear_cookies_settings_key));
        assert youTubeClearCookiesPreference != null;
        youTubeClearCookiesPreference.setOnPreferenceClickListener((Preference p) -> {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.youtube_clear_cookies_prompt)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                        setCookies("youtube", null);

                        // TODO: is there a "safer" way to reset the browser's cookie for login?
                        CookieManager.getInstance().removeAllCookie();
                    })
                    .show();
            return true;
        });
    }

    private void setCookies(final String site,
                            final String cookies) {
        final Context context = getContext();
        assert context != null;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final String key = site + "_cookies";
        final boolean result = prefs.edit().putString(key, cookies).commit();
        if (!result) {
            Log.e(TAG, "Failed to save cookies for " + site);
        }

        DownloaderImpl.getInstance().updateYoutubeSignInCookies(context);
    }
}
