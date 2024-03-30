package org.schabi.newpipe.settings

import android.content.Intent
import leakcanary.LeakCanary.newLeakDisplayActivityIntent
import org.schabi.newpipe.settings.DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI

/**
 * Build variant dependent (BVD) leak canary API implementation for the debug settings fragment.
 * This class is loaded via reflection by
 * [DebugSettingsFragment.DebugSettingsBVDLeakCanaryAPI].
 */
@Suppress("unused") // Class is used but loaded via reflection

class DebugSettingsBVDLeakCanary : DebugSettingsBVDLeakCanaryAPI {
    override fun getNewLeakDisplayActivityIntent(): Intent? {
        return newLeakDisplayActivityIntent()
    }
}
