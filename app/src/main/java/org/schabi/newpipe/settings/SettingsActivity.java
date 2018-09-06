package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;


/*
 * Created by Christian Schabesberger on 31.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * SettingsActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SettingsActivity extends AppCompatActivity implements BasePreferenceFragment.OnPreferenceStartFragmentCallback {

    public static void initSettings(Context context) {
        NewPipeSettings.initSettings(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceBundle) {
        setTheme(ThemeHelper.getSettingsThemeStyle(this));

        super.onCreate(savedInstanceBundle);
        setContentView(R.layout.settings_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceBundle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_holder, new MainSettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                finish();
            } else getSupportFragmentManager().popBackStack();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference preference) {
        Fragment fragment = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
