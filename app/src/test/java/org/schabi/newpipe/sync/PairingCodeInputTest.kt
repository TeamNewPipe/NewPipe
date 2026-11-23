/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingCodeInputTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-25T10:00:00Z"), ZoneOffset.UTC)
    private val issuer = PairingSecurity(clock)
    private val identity = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
    private val invitation = issuer.createInvitation(
        identity,
        "Test phone",
        listOf(address(identity, 32451))
    )

    @Test
    fun `pasted signed invitation tolerates surrounding clipboard whitespace`() {
        val code = issuer.encodeInvitation(invitation)
        val normalized = PairingCodeInput.normalize(" \n\t$code\r\n ")

        assertEquals(code, normalized)
        assertEquals(invitation, PairingSecurity(clock).decodeAndVerifyInvitation(normalized))
    }

    @Test
    fun `blank and oversized inputs are rejected before connecting`() {
        assertThrows(PairingException::class.java) { PairingCodeInput.normalize(" \n\t ") }
        assertThrows(PairingException::class.java) {
            PairingCodeInput.normalize("x".repeat(PairingSecurity.MAX_PAIRING_URI_LENGTH + 1))
        }
    }

    @Test
    fun `manual entry does not repair malformed or non pairing links`() {
        val code = issuer.encodeInvitation(invitation)
        val invalid = listOf(
            "https://example.org/video",
            "wizestream://pair/not-base64!",
            "$code?token=extra",
            "$code#extra",
            code.replace("pair/", "pair/\n")
        )
        invalid.forEach { value ->
            val normalized = PairingCodeInput.normalize(" $value ")
            assertEquals(value, normalized)
            assertThrows(PairingException::class.java) {
                issuer.decodeAndVerifyInvitation(normalized)
            }
        }
    }

    @Test
    fun `expired pasted invitation still goes through the existing verifier`() {
        val code = PairingCodeInput.normalize("\n${issuer.encodeInvitation(invitation)}\n")
        val verifier = PairingSecurity(Clock.offset(clock, Duration.ofMinutes(6)))

        val error = assertThrows(PairingException::class.java) {
            verifier.decodeAndVerifyInvitation(code)
        }

        assertEquals(DeviceSyncSetupFailure.EXPIRED_OR_USED, DeviceSyncSetupDiagnostics.classify(error))
    }

    @Test
    fun `pasted invitation cannot bypass signature verification`() {
        val changed = invitation.copy(deviceName = "Changed after signing")
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
            Json.encodeToString(changed).toByteArray(Charsets.UTF_8)
        )

        val error = assertThrows(PairingException::class.java) {
            issuer.decodeAndVerifyInvitation(PairingCodeInput.normalize(" wizestream://pair/$encoded "))
        }

        assertEquals(DeviceSyncSetupFailure.INVALID_CODE, DeviceSyncSetupDiagnostics.classify(error))
    }

    @Test
    fun `copied invitation keeps the one time token protection`() {
        val requester = DeviceIdentity(generateKeyPair(KeyType.ED25519).first)
        val receiver = PairingSecurity(clock)
        val decoded = receiver.decodeAndVerifyInvitation(
            PairingCodeInput.normalize(" ${issuer.encodeInvitation(invitation)} ")
        )
        val request = receiver.createPairingRequest(
            requester,
            "Test Waydroid",
            listOf(address(requester, 32452)),
            decoded.token
        )

        assertEquals(
            requester.peerId.toBase58(),
            issuer.verifyAndConsumePairingRequest(request, requester.peerId).peerId
        )
        val error = assertThrows(PairingException::class.java) {
            issuer.verifyAndConsumePairingRequest(request, requester.peerId)
        }
        assertEquals(DeviceSyncSetupFailure.EXPIRED_OR_USED, DeviceSyncSetupDiagnostics.classify(error))
    }

    private fun address(device: DeviceIdentity, port: Int): String {
        return "/ip4/127.0.0.1/tcp/$port/p2p/${device.peerId.toBase58()}"
    }
}
