package org.schabi.newpipe.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import org.json.JSONObject;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class ProxySettingsFragment extends BasePreferenceFragment{
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        Preference proxyToken = findPreference(getString(R.string.proxy_token_key));
        Preference enableProxy = findPreference(getString(R.string.enable_proxy_key));

        proxyToken.setOnPreferenceChangeListener((preference, newValue) -> {
            // Return true first to allow the value to be saved
            new Handler().postDelayed(() -> {
                // Now SharedPreferences will have the new value
                ServiceHelper.initServices(getContext());
            }, 100); // 100ms delay should be enough, you can adjust if needed
            return true;
        });

        enableProxy.setOnPreferenceChangeListener((preference, newValue) -> {
            // Return true first to allow the value to be saved
            new Handler().postDelayed(() -> {
                // Now SharedPreferences will have the new value
                ServiceHelper.initServices(getContext());
            }, 100); // 100ms delay should be enough, you can adjust if needed
            return true;
        });

        findPreference(getString(R.string.check_token_validity_key))
                .setOnPreferenceClickListener(preference -> {
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(requireActivity());
                    String token = pref.getString(getString(R.string.proxy_token_key), "");
                    Map<String, List<String>> headers = new HashMap<>();
                    headers.put("Authorization", singletonList("Bearer " + token));
                    try {
                        Response resp = DownloaderImpl.getInstance().get("https://api.pipeplay.dev/token-status", headers);
                        JSONObject jsonObject = new JSONObject(resp.responseBody());
                        if (resp.responseCode() >= 400) {
                            Toast.makeText(requireContext(), "Error: " + jsonObject.getString("error"), Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        if (jsonObject.getBoolean("is_valid")) {
                            Toast.makeText(requireContext(), getString(R.string.valid_until) + ": " + jsonObject.getString("valid_until").replace("T", " ").split("\\.")[0], Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), R.string.token_expired, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });

        findPreference(getString(R.string.donation_encouragement_key))
                .setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(getString(R.string.donation_url)));
                    preference.getContext().startActivity(intent);
                    return true;
                });
    }
}
