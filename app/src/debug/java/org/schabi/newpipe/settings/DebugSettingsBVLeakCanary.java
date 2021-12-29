package org.schabi.newpipe.settings;

import android.content.Intent;

import leakcanary.LeakCanary;

@SuppressWarnings("unused") // Class is used but loaded via reflection
public class DebugSettingsBVLeakCanary
        implements DebugSettingsFragment.DebugSettingsBVLeakCanaryAPI {

    @Override
    public Intent getNewLeakDisplayActivityIntent() {
        return LeakCanary.INSTANCE.newLeakDisplayActivityIntent();
    }
}
