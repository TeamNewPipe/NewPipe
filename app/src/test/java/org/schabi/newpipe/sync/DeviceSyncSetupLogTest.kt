/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.net.BindException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeviceSyncSetupLogTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var repository: DeviceSyncLogRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences = context.getSharedPreferences("device_sync_log", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        repository = DeviceSyncLogRepository(context)
    }

    @Test
    fun `old synchronization log survives adding and reloading setup entries`() {
        preferences.edit().putString(
            "entries",
            """[{"timestampEpochMillis":1234,"background":true,"succeededDevices":2,"failedDevices":1,"sentChanges":5,"receivedChanges":6}]"""
        ).commit()
        val old = repository.entries().single()
        assertNull(old.setup)
        val setup = DeviceSyncSetupDiagnostic(DeviceSyncSetupOperation.PAIR, true)

        repository.recordSetup(setup)
        val reloaded = DeviceSyncLogRepository(context).entries()

        assertEquals(setup, reloaded.first().setup)
        assertEquals(old, reloaded.last())
        assertEquals(2, reloaded.last().succeededDevices)
        assertTrue(reloaded.last().background)
    }

    @Test
    fun `persisted setup failures exclude input addresses and all nested exception text`() {
        val diagnostics = DeviceSyncSetupDiagnostics(SyncAppVersion("test", 1), repository::recordSetup)
        val errors = listOf(
            PairingException("wizestream://pair/private-invitation", IllegalStateException("private-token")),
            PairingException("listener failed", BindException("/ip4/192.168.0.42/tcp/48243"))
        )
        errors.forEach { error ->
            assertThrows(PairingException::class.java) {
                diagnostics.run(DeviceSyncSetupOperation.START_LISTENER) { throw error }
            }
        }

        val reloaded = DeviceSyncLogRepository(context).entries()
        assertEquals(
            listOf(DeviceSyncSetupFailure.LISTENER_UNAVAILABLE, DeviceSyncSetupFailure.UNKNOWN),
            reloaded.map { it.setup?.failure }
        )
        reloaded.forEach { entry ->
            assertNull(entry.fatalError)
            assertTrue(entry.localAddresses.isEmpty())
            assertTrue(entry.attempts.isEmpty())
        }
        val stored = preferences.getString("entries", "").orEmpty()
        listOf("wizestream://", "private-invitation", "private-token", "192.168.0.42", "listener failed").forEach {
            assertFalse(stored.contains(it))
        }
    }

    @Test
    fun `setup records use the existing bounded log and clear action`() {
        repeat(25) { repository.recordSetup(DeviceSyncSetupDiagnostic(DeviceSyncSetupOperation.PAIR, true)) }
        assertEquals(20, DeviceSyncLogRepository(context).entries().size)

        repository.clear()

        assertTrue(DeviceSyncLogRepository(context).entries().isEmpty())
    }
}
