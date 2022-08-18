package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

public class ExoPlayerSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
    }
}
