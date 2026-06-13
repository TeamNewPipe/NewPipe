/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Intent
import leakcanary.LeakCanary.newLeakDisplayActivityIntent

/**
 * Build variant dependent (BVD) leak canary API implementation for the debug settings fragment.
 * This class is loaded via reflection by
 * [DebugSettingsBVDLeakCanaryAPI].
 */
@Suppress("unused") // Class is used but loaded via reflection
class DebugSettingsBVDLeakCanary :

    DebugSettingsBVDLeakCanaryAPI {
    override fun getNewLeakDisplayActivityIntent(): Intent {
        return newLeakDisplayActivityIntent()
    }
}
