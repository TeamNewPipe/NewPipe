package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import org.schabi.newpipe.R;

public class ContentSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.content_settings);

        final ListPreference mainPageContentPref =  (ListPreference) findPreference(getString(R.string.main_page_content_key));

        mainPageContentPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValueO) {
                        final String newValue = newValueO.toString();

                        final String mainPrefOldValue =
                                defaultPreferences.getString(getString(R.string.main_page_content_key), "blank_page");
                        final String mainPrefOldSummary = getMainPagePrefSummery(mainPrefOldValue, mainPageContentPref);

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
                                    mainPageContentPref.setSummary(name);
                                }
                            });
                            selectChannelFragment.setOnCancelListener(new SelectChannelFragment.OnCancelListener() {
                                @Override
                                public void onCancel() {
                                    //defaultPreferences.edit()
                                    //        .putString(getString(R.string.main_page_content_key), mainPrefOldValue).apply();
                                    mainPageContentPref.setSummary(mainPrefOldSummary);
                                    mainPageContentPref.setValue(mainPrefOldValue);
                                }
                            });
                            selectChannelFragment.show(getFragmentManager(), "select_channel");
                        }

                        if(!newValue.equals(getString(R.string.channel_page_key))) {
                            mainPageContentPref.setSummary(getMainPageSummeryByKey(newValue));
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

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/
    private String getMainPagePrefSummery(final String mainPrefOldValue, final ListPreference mainPageContentPref) {
        if(mainPrefOldValue.equals(getString(R.string.channel_page_key))) {
            return defaultPreferences.getString(getString(R.string.main_page_selected_channel_name), "error");
        } else {
            return mainPageContentPref.getSummary().toString();
        }
    }

    private int getMainPageSummeryByKey(final String key) {
        if(key.equals(getString(R.string.blank_page_key))) {
            return R.string.blank_page_summary;
        } else if(key.equals(getString(R.string.kiosk_page_key))) {
            return R.string.kiosk_page_summary;
        } else if(key.equals(getString(R.string.feed_page_key))) {
            return R.string.feed_page_summary;
        } else if(key.equals(getString(R.string.subscription_page_key))) {
            return R.string.subscription_page_summary;
        } else if(key.equals(getString(R.string.channel_page_key))) {
            return R.string.channel_page_summary;
        }
        return R.string.blank_page_summary;
    }
}
