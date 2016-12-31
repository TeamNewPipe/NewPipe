package org.schabi.newpipe;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.util.Objects;

public class Themer extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Objects.equals(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("theme", "1"), "0")) {
            setTheme(R.style.DarkTheme);
        }
    }
}
