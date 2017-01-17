package org.schabi.newpipe;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import java.util.Objects;

import static org.schabi.newpipe.R.attr.theme;

public class Themer extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Objects.equals(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("theme", getResources().getString(R.string.light_theme_title)), getResources().getString(R.string.dark_theme_title))) {
            setTheme(R.style.DarkTheme);
        }
        else setTheme(R.style.BlackTheme);
    }
}