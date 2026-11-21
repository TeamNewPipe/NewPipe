/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import android.app.Application
import androidx.preference.PreferenceManager
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RemotePlaybackConsentTest {
    @Test
    fun receiverDoesNotAcceptCommandsUntilExplicitlyEnabled() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val setting = context.getString(R.string.device_sync_remote_playback_key)
        try {
            preferences.edit().remove(setting).commit()
            assertFalse(RemotePlaybackReceiver.isEnabled(context))
            val request = RemotePlaybackRequest(
                requestId = UUID.randomUUID().toString(),
                action = RemotePlaybackAction.STOP
            )
            assertFalse(RemotePlaybackReceiver.handle(context, request).accepted)
            preferences.edit().putBoolean(setting, true).commit()
            assertTrue(RemotePlaybackReceiver.isEnabled(context))
            preferences.edit().putBoolean(setting, false).commit()
            assertFalse(RemotePlaybackReceiver.handle(context, request).accepted)
        } finally {
            preferences.edit().remove(setting).commit()
        }
    }
}
