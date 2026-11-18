/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CaptionPreferenceStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences(
        "caption-fallback-test",
        Context.MODE_PRIVATE
    )

    @Test
    fun selectedLanguagePersistsAcrossChannelsWithoutOverrides() {
        preferences.edit().clear().commit()
        CaptionPreferenceStore.saveGlobalFallback(context, preferences, "en")
        assertEquals("en", CaptionPreferenceStore.resolve(context, preferences, false, null))
        assertEquals("fr", CaptionPreferenceStore.resolve(context, preferences, true, "fr"))
        assertNull(CaptionPreferenceStore.resolve(context, preferences, true, null))
        assertEquals("en", CaptionPreferenceStore.resolve(context, preferences, false, null))
    }

    @Test
    fun disablingCaptionsClearsFallbackWithoutRemovingChannelOverride() {
        preferences.edit().clear().commit()
        CaptionPreferenceStore.saveGlobalFallback(context, preferences, "en")
        CaptionPreferenceStore.saveGlobalFallback(context, preferences, null)
        assertNull(CaptionPreferenceStore.resolve(context, preferences, false, null))
        assertEquals("fr", CaptionPreferenceStore.resolve(context, preferences, true, "fr"))
        assertEquals(
            false,
            preferences.contains(context.getString(R.string.caption_user_set_key))
        )
    }
}
