package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import org.schabi.newpipe.MainActivity;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
        updateTitle();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(getPreferenceScreen().getTitle());
            }
        }
    }
}
