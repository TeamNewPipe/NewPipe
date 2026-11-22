/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.network.VimeoApiTokenStore

@RunWith(AndroidJUnit4::class)
class VimeoApiSettingsEntryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var previousTokenProvider: () -> String?

    @Before
    fun isolateTokenConfiguration() {
        previousTokenProvider = VimeoPublicApi.setTokenProvider { null }
    }

    @After
    fun restoreTokenConfiguration() {
        VimeoPublicApi.setTokenProvider(previousTokenProvider)
    }

    @Test
    fun registeredSettingsKeepUnverifiedInputOutOfPreferencesAndDiscardItOnCancelAndRecreation() {
        assertEquals(R.xml.vimeo_settings, SettingsResourceRegistry.getInstance().getPreferencesResId(VimeoSettingsFragment::class.java))
        val previousCiphertext = storedCiphertext()
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var canceledInput: TextInputEditText
            scenario.onActivity { activity ->
                val screen = VimeoSettingsFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, screen).commitNow()
                val entry = screen.findPreference<Preference>(context.getString(R.string.vimeo_api_set_key))!!
                val remove = screen.findPreference<Preference>(context.getString(R.string.vimeo_api_remove_key))!!
                assertFalse(entry.isPersistent)
                assertFalse(remove.isPersistent)
                assertFalse(remove.isEnabled)

                entry.onPreferenceClickListener!!.onPreferenceClick(entry)
                val dialog = dialog(screen)!!
                val input = descendants(dialog.window!!.decorView).filterIsInstance<TextInputEditText>().single()
                assertFalse(input.isSaveEnabled)
                assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, input.importantForAutofill)
                }

                input.setText("invalid")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(dialog.isShowing)
                assertEquals(
                    context.getString(R.string.vimeo_api_invalid_token),
                    descendants(dialog.window!!.decorView).filterIsInstance<TextInputLayout>().single().error.toString()
                )
                input.setText(TEST_TOKEN)
                canceledInput = input
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            scenario.onActivity { activity ->
                assertEquals("", canceledInput.text.toString())
                assertFalse(VimeoPublicApi.isConfigured())
                assertNoTokenWasSaved(previousCiphertext)

                val screen = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as VimeoSettingsFragment
                val entry = screen.findPreference<Preference>(context.getString(R.string.vimeo_api_set_key))!!
                entry.onPreferenceClickListener!!.onPreferenceClick(entry)
                val reopenedInput = descendants(dialog(screen)!!.window!!.decorView).filterIsInstance<TextInputEditText>().single()
                assertEquals("", reopenedInput.text.toString())
                reopenedInput.setText(TEST_TOKEN)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val screen = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as VimeoSettingsFragment
                assertNull("Credential entry must not survive activity recreation", dialog(screen))
                assertFalse(VimeoPublicApi.isConfigured())
                assertNoTokenWasSaved(previousCiphertext)
                val entry = screen.findPreference<Preference>(context.getString(R.string.vimeo_api_set_key))!!
                entry.onPreferenceClickListener!!.onPreferenceClick(entry)
                val dialog = dialog(screen)!!
                assertEquals("", descendants(dialog.window!!.decorView).filterIsInstance<TextInputEditText>().single().text.toString())
                dialog.dismiss()
            }
        }
    }

    private fun assertNoTokenWasSaved(previousCiphertext: ByteArray?) {
        assertArrayEquals("Unverified input must not change credential storage", previousCiphertext, storedCiphertext())
        assertFalse(preferences.contains(context.getString(R.string.vimeo_api_set_key)))
        assertFalse(preferences.contains(context.getString(R.string.vimeo_api_remove_key)))
        assertFalse(preferences.all.values.any { it.toString().contains(TEST_TOKEN) })
    }

    private fun storedCiphertext(): ByteArray? = context.noBackupFilesDir.resolve(VimeoApiTokenStore.FILE_NAME)
        .takeIf { it.isFile }?.readBytes()

    private fun dialog(screen: VimeoSettingsFragment): AlertDialog? = VimeoSettingsFragment::class.java
        .getDeclaredField("tokenDialog").apply { isAccessible = true }.get(screen) as AlertDialog?

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }

    companion object {
        private const val TEST_TOKEN = "test-public-token"
    }
}
