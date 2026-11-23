/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.net.BindException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncSetupDiagnosticsTest {
    private val version = SyncAppVersion("test-version", 1)
    private val records = mutableListOf<DeviceSyncSetupDiagnostic>()
    private val diagnostics = DeviceSyncSetupDiagnostics(version, records::add)

    @Test
    fun `successful invitation and pairing record only operation and version`() {
        val result = Any()

        assertEquals("secret-invitation", diagnostics.run(DeviceSyncSetupOperation.CREATE_INVITATION) { "secret-invitation" })
        assertSame(result, diagnostics.run(DeviceSyncSetupOperation.PAIR) { result })
        assertEquals(
            listOf(
                DeviceSyncSetupDiagnostic(DeviceSyncSetupOperation.CREATE_INVITATION, true, localAppVersion = version),
                DeviceSyncSetupDiagnostic(DeviceSyncSetupOperation.PAIR, true, localAppVersion = version)
            ),
            records
        )
        assertFalse(Json.encodeToString(records).contains("secret-invitation"))
    }

    @Test
    fun `listener start failure is recorded and original error rethrown`() {
        val error = ExecutionException("listener private details", BindException("192.168.1.42:48243"))

        assertSame(
            error,
            assertThrows(ExecutionException::class.java) {
                diagnostics.run(DeviceSyncSetupOperation.START_LISTENER) { throw error }
            }
        )

        assertEquals(
            DeviceSyncSetupDiagnostic(
                DeviceSyncSetupOperation.START_LISTENER,
                false,
                DeviceSyncSetupFailure.LISTENER_UNAVAILABLE,
                version
            ),
            records.single()
        )
        assertFalse(Json.encodeToString(records).contains("192.168.1.42"))
    }

    @Test
    fun `successful listener starts do not crowd out useful diagnostics`() {
        repeat(30) { diagnostics.run(DeviceSyncSetupOperation.START_LISTENER) { Unit } }

        assertTrue(records.isEmpty())
    }

    @Test
    fun `unknown failure and nested secrets are replaced with an allowlisted code`() {
        val error = PairingException(
            "wizestream://pair/secret-invitation",
            IllegalStateException("token-secret", IllegalArgumentException("/ip4/192.168.1.42/tcp/48243"))
        )

        assertSame(
            error,
            assertThrows(PairingException::class.java) {
                diagnostics.run(DeviceSyncSetupOperation.PAIR) { throw error }
            }
        )

        assertEquals(DeviceSyncSetupFailure.UNKNOWN, records.single().failure)
        val stored = Json.encodeToString(records)
        listOf("wizestream://", "secret-invitation", "token-secret", "192.168.1.42", "/ip4/").forEach {
            assertFalse(stored.contains(it))
        }
    }

    @Test
    fun `malformed invitation failure does not prevent a later successful attempt`() {
        assertThrows(PairingException::class.java) {
            diagnostics.run(DeviceSyncSetupOperation.PAIR) {
                PairingSecurity().decodeAndVerifyInvitation(PairingCodeInput.normalize(" wizestream://pair/invalid! "))
            }
        }
        diagnostics.run(DeviceSyncSetupOperation.PAIR) { "paired" }

        assertEquals(listOf(false, true), records.map { it.succeeded })
        assertEquals(DeviceSyncSetupFailure.INVALID_CODE, records.first().failure)
        assertEquals(null, records.last().failure)
    }

    @Test
    fun `cancellation is propagated without a failed pairing record`() {
        val cancellation = CancellationException("screen closed")

        assertSame(
            cancellation,
            assertThrows(CancellationException::class.java) {
                diagnostics.run(DeviceSyncSetupOperation.PAIR) { throw cancellation }
            }
        )
        assertTrue(records.isEmpty())
    }

    @Test
    fun `nested timeout remains actionable without persisting its text`() {
        val error = PairingException("Could not reach the other device", SocketTimeoutException("private address"))

        assertEquals(DeviceSyncSetupFailure.TIMEOUT, DeviceSyncSetupDiagnostics.classify(error))
    }

    @Test
    fun `cyclic exception causes cannot hang setup diagnostics`() {
        val first = IllegalStateException("first secret")
        val second = IllegalArgumentException("second secret", first)
        first.initCause(second)

        assertEquals(DeviceSyncSetupFailure.UNKNOWN, DeviceSyncSetupDiagnostics.classify(first))
    }

    @Test
    fun `unavailable log storage cannot change the original operation result`() {
        val unavailable = DeviceSyncSetupDiagnostics(version) { throw IllegalStateException("storage unavailable") }
        val error = PairingException("The pairing invitation has expired")

        assertEquals("paired", unavailable.run(DeviceSyncSetupOperation.PAIR) { "paired" })
        assertSame(
            error,
            assertThrows(PairingException::class.java) {
                unavailable.run(DeviceSyncSetupOperation.PAIR) { throw error }
            }
        )
    }
}
