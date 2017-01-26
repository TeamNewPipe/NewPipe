package org.schabi.newpipe;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

public class ThemableActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("theme", getResources().getString(R.string.light_theme_title)).
                            equals(getResources().getString(R.string.dark_theme_title)))  {
                setTheme(R.style.DarkTheme);
        }
    }
}