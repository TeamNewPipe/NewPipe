/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlaybackModelsTest {
    private val id = UUID.randomUUID().toString()

    @Test
    fun onlyHttpsMediaAndBoundedSeekRequestsAreAccepted() {
        val valid = RemotePlaybackRequest(
            requestId = id,
            action = RemotePlaybackAction.OPEN,
            serviceId = 0,
            url = "https://www.youtube.com/watch?v=abcdefghijk",
            title = "Video",
            positionMillis = 1_000
        )
        RemotePlaybackValidation.validateRequest(valid)
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateRequest(
                    valid.copy(url = "file:///sdcard/movie.mp4")
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateRequest(
                    valid.copy(url = "https://example.com/private#fragment")
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateRequest(
                    valid.copy(title = "a".repeat(300))
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateRequest(
                    RemotePlaybackRequest(
                        requestId = id,
                        action = RemotePlaybackAction.SEEK,
                        positionMillis = -1
                    )
                )
            }.isFailure
        )
    }

    @Test
    fun wireCodecRoundTripAndResponseRequestBinding() {
        val request = RemotePlaybackRequest(requestId = id, action = RemotePlaybackAction.STATUS)
        val decoded = RemotePlaybackCodec.decodeRequest(RemotePlaybackCodec.encodeRequest(request))
        assertEquals(request, decoded)
        val response = RemotePlaybackResponse(
            accepted = true,
            requestId = id,
            status = RemotePlaybackStatus(playing = true, title = "Video", positionMillis = 9_000)
        )
        assertEquals(
            response,
            RemotePlaybackCodec.decodeResponse(RemotePlaybackCodec.encodeResponse(response))
        )
        RemotePlaybackValidation.validateResponse(response, id)
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateResponse(response, UUID.randomUUID().toString())
            }.isFailure
        )
        assertFalse(
            runCatching { RemotePlaybackValidation.validateRequest(request) }.isFailure
        )
    }

    @Test
    fun unknownFieldsAndCommandsAreRejected() {
        assertTrue(
            runCatching {
                RemotePlaybackCodec.decodeRequest(
                    """{"version":1,"requestId":"$id","action":"STATUS","token":"private"}"""
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                RemotePlaybackValidation.validateRequest(
                    RemotePlaybackRequest(requestId = id, action = RemotePlaybackAction.PAUSE, url = "https://x")
                )
            }.isFailure
        )
    }
}
