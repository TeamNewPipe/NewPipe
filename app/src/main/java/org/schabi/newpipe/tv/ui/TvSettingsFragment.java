/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.os.Bundle;

import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.schabi.newpipe.R;

public class TvSettingsFragment extends LeanbackSettingsFragmentCompat {

    @Override
    public void onPreferenceStartInitialScreen() {
        startPreferenceFragment(new TvPreferenceFragment());
    }

    @Override
    public boolean onPreferenceStartFragment(final PreferenceFragmentCompat caller,
                                             final Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(final PreferenceFragmentCompat caller,
                                           final PreferenceScreen pref) {
        return false;
    }

    public static class TvPreferenceFragment extends LeanbackPreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.tv_preferences, rootKey);
        }
    }
}
