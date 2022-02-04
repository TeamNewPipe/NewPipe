package org.schabi.newpipe.settings;

import android.content.Intent;

import leakcanary.LeakCanary;

/**
 * Build variant dependent (BVD) leak canary API implementation for the debug settings fragment.
 * This class is loaded via reflection by
 * {@link DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI}.
 */
@SuppressWarnings("unused") // Class is used but loaded via reflection
public class DebugSettingsBVDLeakCanary
        implements DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI {

    @Override
    public Intent getNewLeakDisplayActivityIntent() {
        return LeakCanary.INSTANCE.newLeakDisplayActivityIntent();
    }
}
