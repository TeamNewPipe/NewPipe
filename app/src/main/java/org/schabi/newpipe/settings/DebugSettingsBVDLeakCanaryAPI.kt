/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Intent

/**
 * Build variant dependent (BVD) leak canary API.
 * Why is LeakCanary not used directly? Because it can't be assured to be available.
 */
interface DebugSettingsBVDLeakCanaryAPI {
    fun getNewLeakDisplayActivityIntent(): Intent

    companion object {
        const val IMPL_CLASS = "org.schabi.newpipe.settings.DebugSettingsBVDLeakCanary"
    }
}
