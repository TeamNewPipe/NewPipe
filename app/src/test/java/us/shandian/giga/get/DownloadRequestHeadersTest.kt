/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package us.shandian.giga.get

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend

class DownloadRequestHeadersTest {
    @Test
    fun oldMissionDescriptorWithoutHeaderFieldStillDeserializes() {
        // The pre-header format has DownloadMission UID6 and Mission UID1. Omitted fields must
        // be read as defaults rather than invalidating an already queued download.
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeShort(0xaced)
                data.writeShort(5)
                data.writeByte(0x73)
                descriptor(data, "us.shandian.giga.get.DownloadMission", 6)
                descriptor(data, "us.shandian.giga.get.Mission", 1)
                data.writeByte(0x70)
            }
        }.toByteArray()
        val mission = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as DownloadMission }
        assertNull(mission.resourceRequestHeaders)
        assertTrue(DownloadRequestHeaders.at(mission, 0).isEmpty())
    }

    @Test
    fun resourceHeadersSurviveSerializationAndRecoveryReplacesOrClearsOnlyTheSelectedResource() {
        val mission = mission("https://example.org/video")
        mission.urls = arrayOf("https://example.org/video", "https://example.org/audio")
        mission.resourceRequestHeaders = DownloadRequestHeaders.copy(listOf(mapOf("Authorization" to "old-video"), mapOf("Authorization" to "audio")))
        DownloadRequestHeaders.replace(mission, 0, mapOf("Authorization" to "refreshed-video"))
        assertEquals("audio", DownloadRequestHeaders.at(mission, 1)["Authorization"])
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(mission) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as DownloadMission }
        assertEquals("refreshed-video", DownloadRequestHeaders.at(restored, 0)["Authorization"])
        DownloadRequestHeaders.replace(restored, 0, null)
        assertTrue(DownloadRequestHeaders.at(restored, 0).isEmpty())
        assertEquals("audio", DownloadRequestHeaders.at(restored, 1)["Authorization"])
    }

    @Test
    fun redirectPolicyRecognizesPortsSchemesAndCaseInsensitiveCredentialNames() {
        val headers = mapOf("AUTHORIZATION" to "fixture", "cookie" to "session=fixture", "Referer" to "https://site.example/", "User-Agent" to "Fixture")
        val origin = URL("https://MEDIA.example/video")
        assertEquals(headers, DownloadRequestHeaders.afterRedirect(headers, origin, URL("https://media.example:443/other")))
        for (target in listOf("https://other.example/", "https://media.example:8443/", "http://media.example/")) {
            val redirected = DownloadRequestHeaders.afterRedirect(headers, origin, URL(target))
            assertEquals(setOf("Referer", "User-Agent"), redirected.keys)
        }
    }

    @Test
    fun headAndRangeRequestsKeepHeadersOnSameOriginAndDropCredentialsAcrossRedirects() = withoutInvidious {
        LocalServer(listOf(Reply(200))).use { target ->
            LocalServer(listOf(Reply(302, "/same-origin"), Reply(302, target.url))).use { source ->
                val mission = mission(source.url)
                mission.resourceRequestHeaders = DownloadRequestHeaders.copy(
                    listOf(mapOf("Authorization" to "Bearer fixture", "Cookie" to "session=fixture", "Referer" to "https://site.example/", "Range" to "bytes=999-"))
                )
                val connection = mission.openConnection(source.url, true, 10, 19, "fixture-etag")
                try {
                    assertEquals(200, connection.responseCode)
                } finally {
                    connection.disconnect()
                }
                for (request in source.requests()) {
                    assertTrue(request.line.startsWith("HEAD "))
                    assertEquals("Bearer fixture", request.headers["authorization"])
                    assertEquals("session=fixture", request.headers["cookie"])
                    assertEquals("bytes=10-19", request.headers["range"])
                    assertEquals("fixture-etag", request.headers["if-range"])
                }
                val redirected = target.requests().single()
                assertFalse(redirected.headers.containsKey("authorization"))
                assertFalse(redirected.headers.containsKey("cookie"))
                assertEquals("https://site.example/", redirected.headers["referer"])
                assertEquals("bytes=10-19", redirected.headers["range"])
            }
        }
    }

    @Test
    fun initialSizeProbeUsesTheSelectedResourcesHeaders() = withoutInvidious {
        LocalServer(listOf(Reply(200))).use { server ->
            val mission = mission("https://unused.example/video")
            mission.urls = arrayOf("https://unused.example/video", server.url)
            mission.resourceRequestHeaders = DownloadRequestHeaders.copy(listOf(mapOf("Authorization" to "video"), mapOf("Authorization" to "audio")))
            val connection = mission.openConnection(1, true, 0, 0)
            try {
                assertEquals(200, connection.responseCode)
            } finally {
                connection.disconnect()
            }
            assertEquals("audio", server.requests().single().headers["authorization"])
            assertEquals(0, mission.current)
        }
    }

    private fun mission(url: String) = DownloadMission(arrayOf(url), null, 'v', null)

    private fun descriptor(output: DataOutputStream, name: String, uid: Long) {
        output.writeByte(0x72)
        output.writeUTF(name)
        output.writeLong(uid)
        output.writeByte(2)
        output.writeShort(0)
        output.writeByte(0x78)
    }

    private fun withoutInvidious(block: () -> Unit) {
        val previousEnabled = InvidiousBackend.isEnabled()
        val previousInstance = runCatching { InvidiousBackend.getInstance() }.getOrDefault("")
        try {
            InvidiousBackend.configure(false, "")
            block()
        } finally {
            InvidiousBackend.configure(previousEnabled, previousInstance)
        }
    }

    private data class Reply(val code: Int, val location: String? = null)
    private data class Request(val line: String, val headers: Map<String, String>)

    private class LocalServer(replies: List<Reply>) : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5000 }
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${socket.localPort}/media"
        private val received = executor.submit<List<Request>> {
            replies.map { reply ->
                socket.accept().use { connection ->
                    connection.soTimeout = 5000
                    val input = connection.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val line = input.readLine()
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val header = input.readLine() ?: break
                        if (header.isEmpty()) break
                        val split = header.indexOf(':')
                        if (split > 0) headers[header.substring(0, split).lowercase(Locale.ROOT)] = header.substring(split + 1).trim()
                    }
                    val location = reply.location?.let { "Location: $it\r\n" }.orEmpty()
                    val response = "HTTP/1.1 ${reply.code} Fixture\r\n${location}Content-Length: 0\r\nConnection: close\r\n\r\n"
                    connection.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
                    connection.getOutputStream().flush()
                    Request(line, headers)
                }
            }
        }

        fun requests(): List<Request> = received.get(5, TimeUnit.SECONDS)

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }
    }
}
