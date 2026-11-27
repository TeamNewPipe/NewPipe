/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import info.guardianproject.panic.Panic
import info.guardianproject.panic.PanicResponder
import net.newpipe.app.preferences.PanicPreferences
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.R

class PanicActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appName = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
    private lateinit var preferences: SharedPreferences
    private lateinit var savedPreferences: Map<String, *>
    private val keys = listOf(
        PanicResponder.PREF_TRIGGER_PACKAGE_NAME,
        PanicPreferences.KEY_TRIGGER_APPS
    )

    @Before
    fun setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        savedPreferences = preferences.all.filterKeys { it in keys }
        val editor = preferences.edit()
        keys.forEach { editor.remove(it) }
        editor.commit()
    }

    @After
    fun tearDown() {
        val editor = preferences.edit()
        keys.forEach { key ->
            when (val value = savedPreferences[key]) {
                is String -> editor.putString(key, value)
                null -> editor.remove(key)
                else -> error("Unexpected preference value for $key")
            }
        }
        editor.commit()
    }

    @Test
    fun confirmingFirstTriggerRegistersItAndDisplaysItsName() {
        launchConnect().use {
            assertConfirmationDialog()
            assertNoTriggerRegistered()

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertConfirmationDialog()
            assertNoTriggerRegistered()

            composeRule.onNodeWithText(context.getString(R.string.ok)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.panic_app_dialog_title)).assertDoesNotExist()
            composeRule.onNodeWithText(appName).performScrollTo().assertIsDisplayed()
            composeRule.runOnIdle {
                assertEquals(context.packageName, preferences.getString(PanicResponder.PREF_TRIGGER_PACKAGE_NAME, null))
                val storedApp = JSONObject(requireNotNull(preferences.getString(PanicPreferences.KEY_TRIGGER_APPS, null)))
                assertEquals(context.packageName, storedApp.getString("packageName"))
                assertEquals(appName, storedApp.getString("name"))
            }
        }
    }

    @Test
    fun cancellingFirstTriggerDoesNotRegisterIt() {
        launchConnect().use { scenario ->
            assertConfirmationDialog()
            assertNoTriggerRegistered()

            composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                scenario.state == Lifecycle.State.DESTROYED
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertNoTriggerRegistered()
        }
    }

    private fun launchConnect(): ActivityScenario<PanicActivity> = ActivityScenario.launch(
        Intent(context, PanicActivity::class.java)
            .setAction(Panic.ACTION_CONNECT)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
    )

    private fun assertConfirmationDialog() {
        composeRule.onNodeWithText(context.getString(R.string.panic_app_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.panic_app_dialog_message, appName)).assertIsDisplayed()
    }

    private fun assertNoTriggerRegistered() {
        assertFalse(preferences.contains(PanicResponder.PREF_TRIGGER_PACKAGE_NAME))
        assertFalse(preferences.contains(PanicPreferences.KEY_TRIGGER_APPS))
    }
}
