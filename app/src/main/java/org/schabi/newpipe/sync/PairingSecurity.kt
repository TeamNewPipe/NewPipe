/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.PubKey
import io.libp2p.core.crypto.marshalPublicKey
import io.libp2p.core.crypto.unmarshalPublicKey
import io.libp2p.core.multiformats.Multiaddr
import java.net.URI
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PairingSecurity(
    private val clock: Clock = Clock.systemUTC(),
    private val secureRandom: SecureRandom = SecureRandom()
) {
    private val pendingInvitations = ConcurrentHashMap<String, Long>()

    fun createInvitation(
        identity: DeviceIdentity,
        deviceName: String,
        addresses: List<String>,
        lifetimeMillis: Long = DEFAULT_INVITATION_LIFETIME_MILLIS
    ): PairingInvitation {
        require(lifetimeMillis in 1..MAX_INVITATION_LIFETIME_MILLIS)

        val now = clock.millis()
        val token = encodeBase64(ByteArray(TOKEN_SIZE_BYTES).also(secureRandom::nextBytes))
        val unsigned = PairingInvitation(
            version = SYNC_PROTOCOL_VERSION,
            peerId = identity.peerId.toBase58(),
            publicKey = encodeBase64(marshalPublicKey(identity.privateKey.publicKey())),
            deviceName = normalizeDeviceName(deviceName),
            addresses = validateAddresses(identity.peerId, addresses),
            issuedAtEpochMillis = now,
            expiresAtEpochMillis = now + lifetimeMillis,
            token = token,
            signature = ""
        )
        val invitation = unsigned.copy(
            signature = sign(identity.privateKey, invitationSigningBytes(unsigned))
        )
        pendingInvitations[token] = invitation.expiresAtEpochMillis
        pruneExpiredInvitations(now)
        return invitation
    }

    @Throws(PairingException::class)
    fun encodeInvitation(invitation: PairingInvitation): String {
        verifyInvitation(invitation)
        val encoded = encodeBase64(JSON.encodeToString(invitation).toByteArray(Charsets.UTF_8))
        return "$PAIRING_URI_PREFIX$encoded"
    }

    @Throws(PairingException::class)
    fun decodeAndVerifyInvitation(value: String): PairingInvitation {
        if (value.length > MAX_PAIRING_URI_LENGTH) {
            throw PairingException("The pairing code is too large")
        }

        val encoded = try {
            val uri = URI(value)
            if (
                uri.scheme != PAIRING_URI_SCHEME ||
                uri.host != PAIRING_URI_HOST ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.userInfo != null
            ) {
                throw PairingException("This is not a WizeStream pairing code")
            }
            uri.rawPath.removePrefix("/").takeIf {
                it.isNotEmpty() && !it.contains('/')
            } ?: throw PairingException("The pairing code has no invitation")
        } catch (error: PairingException) {
            throw error
        } catch (error: Exception) {
            throw PairingException("This is not a valid pairing code", error)
        }

        val invitation = try {
            JSON.decodeFromString<PairingInvitation>(
                decodeBase64(encoded).toString(Charsets.UTF_8)
            )
        } catch (error: Exception) {
            throw PairingException("The pairing invitation is malformed", error)
        }
        verifyInvitation(invitation)
        return invitation
    }

    @Throws(PairingException::class)
    fun verifyInvitation(invitation: PairingInvitation) {
        verifyVersion(invitation.version)
        val publicKey = verifyPeerDescriptor(
            invitation.peerId,
            invitation.publicKey,
            invitation.deviceName,
            invitation.addresses
        )
        val now = clock.millis()
        if (invitation.issuedAtEpochMillis > now + MAX_CLOCK_SKEW_MILLIS) {
            throw PairingException("The pairing invitation is not valid yet")
        }
        if (invitation.expiresAtEpochMillis < now) {
            throw PairingException("The pairing invitation has expired")
        }
        if (
            invitation.expiresAtEpochMillis <= invitation.issuedAtEpochMillis ||
            invitation.expiresAtEpochMillis - invitation.issuedAtEpochMillis >
            MAX_INVITATION_LIFETIME_MILLIS
        ) {
            throw PairingException("The pairing invitation has an invalid lifetime")
        }
        if (decodeBase64Checked(invitation.token, "invitation token").size != TOKEN_SIZE_BYTES) {
            throw PairingException("The pairing invitation token has an invalid length")
        }
        verifySignature(
            publicKey,
            invitationSigningBytes(invitation.copy(signature = "")),
            invitation.signature
        )
    }

    internal fun createPairingRequest(
        identity: DeviceIdentity,
        deviceName: String,
        addresses: List<String>,
        invitationToken: String
    ): PairingRequest {
        val unsigned = PairingRequest(
            version = SYNC_PROTOCOL_VERSION,
            peerId = identity.peerId.toBase58(),
            publicKey = encodeBase64(marshalPublicKey(identity.privateKey.publicKey())),
            deviceName = normalizeDeviceName(deviceName),
            addresses = validateAddresses(identity.peerId, addresses),
            issuedAtEpochMillis = clock.millis(),
            invitationToken = invitationToken,
            signature = ""
        )
        return unsigned.copy(
            signature = sign(identity.privateKey, requestSigningBytes(unsigned))
        )
    }

    @Throws(PairingException::class)
    internal fun verifyAndConsumePairingRequest(
        request: PairingRequest,
        remotePeerId: PeerId
    ): TrustedPeer {
        verifyVersion(request.version)
        if (request.peerId != remotePeerId.toBase58()) {
            throw PairingException("The connected device identity does not match its request")
        }
        val publicKey = verifyPeerDescriptor(
            request.peerId,
            request.publicKey,
            request.deviceName,
            request.addresses
        )
        val now = clock.millis()
        if (kotlin.math.abs(now - request.issuedAtEpochMillis) > MAX_REQUEST_AGE_MILLIS) {
            throw PairingException("The pairing request is stale")
        }
        verifySignature(
            publicKey,
            requestSigningBytes(request.copy(signature = "")),
            request.signature
        )
        if (!consumeInvitation(request.invitationToken, now)) {
            throw PairingException("The pairing invitation is expired or was already used")
        }
        return request.toTrustedPeer(now)
    }

    fun invitationToTrustedPeer(invitation: PairingInvitation): TrustedPeer {
        verifyInvitation(invitation)
        return invitation.toTrustedPeer(clock.millis())
    }

    private fun consumeInvitation(token: String, now: Long): Boolean {
        val expiry = pendingInvitations.remove(token) ?: return false
        return expiry >= now
    }

    private fun pruneExpiredInvitations(now: Long) {
        pendingInvitations.entries.removeIf { it.value < now }
    }

    @Throws(PairingException::class)
    private fun verifyPeerDescriptor(
        peerIdValue: String,
        publicKeyValue: String,
        deviceName: String,
        addresses: List<String>
    ): PubKey {
        if (deviceName.isBlank() || deviceName.length > MAX_DEVICE_NAME_LENGTH) {
            throw PairingException("The device name is invalid")
        }
        val peerId = try {
            PeerId.fromBase58(peerIdValue)
        } catch (error: Exception) {
            throw PairingException("The PeerID is invalid", error)
        }
        val publicKey = try {
            unmarshalPublicKey(decodeBase64(publicKeyValue))
        } catch (error: Exception) {
            throw PairingException("The public key is invalid", error)
        }
        if (PeerId.fromPubKey(publicKey) != peerId) {
            throw PairingException("The public key does not belong to the advertised PeerID")
        }
        try {
            validateAddresses(peerId, addresses)
        } catch (error: IllegalArgumentException) {
            throw PairingException(error.message ?: "The network addresses are invalid", error)
        }
        return publicKey
    }

    private fun validateAddresses(peerId: PeerId, addresses: List<String>): List<String> {
        require(addresses.isNotEmpty()) { "At least one network address is required" }
        require(addresses.size <= MAX_PAIRING_ADDRESSES) { "Too many network addresses" }
        return addresses.map { value ->
            require(value.length <= MAX_MULTIADDRESS_LENGTH) { "A network address is too long" }
            val address = Multiaddr(value)
            require(address.getPeerId() == peerId) {
                "Every network address must contain the advertised PeerID"
            }
            address.toString()
        }.distinct()
    }

    private fun normalizeDeviceName(deviceName: String): String {
        return deviceName.trim().replace(WHITESPACE_REGEX, " ").take(MAX_DEVICE_NAME_LENGTH)
            .ifBlank { DEFAULT_DEVICE_NAME }
    }

    private fun verifyVersion(version: Int) {
        if (version != SYNC_PROTOCOL_VERSION) {
            throw PairingException("Unsupported pairing protocol version: $version")
        }
    }

    private fun sign(privateKey: PrivKey, bytes: ByteArray): String {
        return encodeBase64(privateKey.sign(bytes))
    }

    @Throws(PairingException::class)
    private fun verifySignature(publicKey: PubKey, bytes: ByteArray, signatureValue: String) {
        val signature = decodeBase64Checked(signatureValue, "signature")
        if (!publicKey.verify(bytes, signature)) {
            throw PairingException("The pairing signature is invalid")
        }
    }

    private fun invitationSigningBytes(invitation: PairingInvitation): ByteArray {
        return JSON.encodeToString(invitation).toByteArray(Charsets.UTF_8)
    }

    private fun requestSigningBytes(request: PairingRequest): ByteArray {
        return JSON.encodeToString(request).toByteArray(Charsets.UTF_8)
    }

    private fun PairingInvitation.toTrustedPeer(pairedAt: Long) = TrustedPeer(
        peerId = peerId,
        publicKey = publicKey,
        deviceName = deviceName,
        addresses = addresses,
        pairedAtEpochMillis = pairedAt
    )

    private fun PairingRequest.toTrustedPeer(pairedAt: Long) = TrustedPeer(
        peerId = peerId,
        publicKey = publicKey,
        deviceName = deviceName,
        addresses = addresses,
        pairedAtEpochMillis = pairedAt
    )

    private fun decodeBase64Checked(value: String, label: String): ByteArray {
        return try {
            decodeBase64(value)
        } catch (error: Exception) {
            throw PairingException("The $label is invalid", error)
        }
    }

    companion object {
        const val DEFAULT_INVITATION_LIFETIME_MILLIS = 5 * 60 * 1000L

        private const val MAX_INVITATION_LIFETIME_MILLIS =
            DEFAULT_INVITATION_LIFETIME_MILLIS
        private const val MAX_CLOCK_SKEW_MILLIS = 60 * 1000L
        private const val MAX_REQUEST_AGE_MILLIS = 2 * 60 * 1000L
        private const val TOKEN_SIZE_BYTES = 32
        internal const val MAX_PAIRING_URI_LENGTH = 16 * 1024
        private const val PAIRING_URI_SCHEME = "wizestream"
        private const val PAIRING_URI_HOST = "pair"
        private const val PAIRING_URI_PREFIX = "$PAIRING_URI_SCHEME://$PAIRING_URI_HOST/"
        private const val DEFAULT_DEVICE_NAME = "Android device"
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        internal fun encodeWireMessage(message: SyncWireMessage): String {
            return JSON.encodeToString(message)
        }

        internal fun decodeWireMessage(value: String): SyncWireMessage {
            return JSON.decodeFromString(value)
        }

        private fun encodeBase64(value: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
        }

        private fun decodeBase64(value: String): ByteArray {
            return Base64.getUrlDecoder().decode(value)
        }
    }
}
