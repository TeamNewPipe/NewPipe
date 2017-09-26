package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.util.Log;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

public class ContentSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.content_settings);

        findPreference(getString(R.string.main_page_content_key))
                .setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueO) {
                        final String newValue = newValueO.toString();

                        if(newValue.equals(getString(R.string.kiosk_page_key))) {
                            //todo on multyservice support show a kiosk an service selector here
                        } else if(newValue.equals(getString(R.string.channel_page_key))) {
                            SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
                            selectChannelFragment.setOnSelectedLisener(new SelectChannelFragment.OnSelectedLisener() {
                                @Override
                                public void onChannelSelected(String url, String name, int service) {
                                    defaultPreferences.edit()
                                            .putInt(getString(R.string.main_page_selected_service), service).apply();
                                    defaultPreferences.edit()
                                            .putString(getString(R.string.main_page_selected_channel_url), url).apply();
                                    defaultPreferences.edit()
                                            .putString(getString(R.string.main_page_selected_channel_name), name).apply();

                                    //change summery
                                    Preference pref = findPreference(getString(R.string.main_page_content_key));
                                    pref.setSummary(name);


                                }
                            });
                            selectChannelFragment.show(getFragmentManager(), "select_channel");
                        }

                        return true;
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();

        final String mainPageContentKey = getString(R.string.main_page_content_key);
        if(defaultPreferences.getString(mainPageContentKey,
                getString(R.string.blank_page_key))
                .equals(getString(R.string.channel_page_key))) {
            Preference pref = findPreference(getString(R.string.main_page_content_key));
            pref.setSummary(defaultPreferences.getString(getString(R.string.main_page_selected_channel_name), "error"));
        }
    }
}
