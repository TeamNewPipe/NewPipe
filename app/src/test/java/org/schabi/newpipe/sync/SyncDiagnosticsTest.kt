package org.schabi.newpipe.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncDiagnosticsTest {
    @Test
    fun exchangesVersionAndAttemptMetadata() {
        val attemptId = "11111111-1111-1111-1111-111111111111"
        val request = SyncDiagnosticsRequest(
            attemptId = attemptId,
            appVersion = SyncAppVersion("1.43.0", 14300)
        )
        SyncDiagnosticsValidation.validateRequest(request)
        assertEquals(
            request,
            SyncDiagnosticsCodec.decodeRequest(SyncDiagnosticsCodec.encodeRequest(request))
        )

        val response = SyncDiagnosticsResponse(
            accepted = true,
            attemptId = attemptId,
            appVersion = SyncAppVersion("1.42.0", 14200),
            syncProtocolVersion = SYNC_PROTOCOL_VERSION
        )
        SyncDiagnosticsValidation.validateResponse(response, attemptId)
        assertEquals(
            response,
            SyncDiagnosticsCodec.decodeResponse(SyncDiagnosticsCodec.encodeResponse(response))
        )
    }

    @Test
    fun rejectsMismatchedAttemptIdsAndInvalidVersions() {
        assertThrows(SyncDiagnosticsException::class.java) {
            SyncDiagnosticsValidation.validateRequest(
                SyncDiagnosticsRequest(
                    attemptId = "not-a-uuid",
                    appVersion = SyncAppVersion("1.43.0", 14300)
                )
            )
        }
        assertThrows(SyncDiagnosticsException::class.java) {
            SyncDiagnosticsValidation.validateResponse(
                SyncDiagnosticsResponse(
                    accepted = true,
                    attemptId = "22222222-2222-2222-2222-222222222222",
                    appVersion = SyncAppVersion("1.42.0", 14200),
                    syncProtocolVersion = SYNC_PROTOCOL_VERSION
                ),
                "11111111-1111-1111-1111-111111111111"
            )
        }
    }
}
