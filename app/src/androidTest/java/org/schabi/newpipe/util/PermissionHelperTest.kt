/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class PermissionHelperTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun activityContextShowsOverlayPermissionDialog() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        assumeFalse(Settings.canDrawOverlays(applicationContext))

        // The permission request overlays the dialog on app start.
        // Grant the permission to get rid of it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                applicationContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    assertTrue(activity.isUiContext)
                }
                assertFalse(PermissionHelper.isPopupEnabledElseAsk(activity))
            }

            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

            automation.waitForIdle(500, 5000)
            val root = automation.rootInActiveWindow
            assertTrue(
                "The overlay permission dialog should be visible",
                root != null &&
                    root.findAccessibilityNodeInfosByText(
                        applicationContext.getString(R.string.permission_display_over_apps)
                    ).isNotEmpty()
            )
        }
    }
}
