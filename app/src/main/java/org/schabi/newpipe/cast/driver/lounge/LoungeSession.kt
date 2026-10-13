package org.schabi.newpipe.cast.driver.lounge

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastPlayerState
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastStateListener

class LoungeSession(
    override val device: CastDevice,
    private val listener: CastStateListener,
    private val client: OkHttpClient = OkHttpClient()
) : CastSession {

    private val isConnected = AtomicBoolean(false)
    private val reqOffset = AtomicInteger(0)
    private val sessionId = UUID.randomUUID().toString()
    private var activeLoungeToken: String? = device.loungeToken

    private var currentPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var playerState = CastPlayerState.IDLE

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val LOUNGE_BIND_URL = "https://www.youtube.com/api/lounge/bc/bind"
    }

    override fun connect() {
        if (isConnected.get()) return

        Thread {
            try {
                if (activeLoungeToken.isNullOrEmpty()) {
                    activeLoungeToken = resolveLoungeTokenFromDial()
                }

                if (activeLoungeToken.isNullOrEmpty()) {
                    throw IllegalStateException("Unable to obtain YouTube Lounge token for ${device.name}")
                }

                isConnected.set(true)
                mainHandler.post { listener.onSessionStarted(this) }
            } catch (e: Exception) {
                disconnect()
                mainHandler.post { listener.onSessionEnded(this, e) }
            }
        }.start()
    }

    override fun disconnect() {
        if (!isConnected.getAndSet(false)) return
        mainHandler.post { listener.onSessionEnded(this, null) }
    }

    override fun isConnected(): Boolean = isConnected.get()

    override fun loadMedia(
        streamUrl: String,
        title: String,
        artist: String?,
        thumbnailUrl: String?,
        startPositionMs: Long
    ) {
        val videoId = extractYouTubeVideoId(streamUrl) ?: return
        sendCommand(
            mapOf(
                "req0__sc" to "setPlaylist",
                "req0_videoId" to videoId,
                "req0_currentTime" to (startPositionMs / 1000.0).toString()
            )
        )
        currentPositionMs = startPositionMs
        playerState = CastPlayerState.PLAYING
        mainHandler.post {
            listener.onPlaybackStateChanged(playerState)
            listener.onProgressUpdated(currentPositionMs, durationMs)
        }
    }

    override fun play() {
        sendCommand(mapOf("req0__sc" to "play"))
        playerState = CastPlayerState.PLAYING
        mainHandler.post { listener.onPlaybackStateChanged(playerState) }
    }

    override fun pause() {
        sendCommand(mapOf("req0__sc" to "pause"))
        playerState = CastPlayerState.PAUSED
        mainHandler.post { listener.onPlaybackStateChanged(playerState) }
    }

    override fun seekTo(positionMs: Long) {
        sendCommand(
            mapOf(
                "req0__sc" to "seekTo",
                "req0_newTime" to (positionMs / 1000.0).toString()
            )
        )
        currentPositionMs = positionMs
        mainHandler.post { listener.onProgressUpdated(currentPositionMs, durationMs) }
    }

    override fun setVolume(volume: Float) {
        val volInt = (volume * 100).toInt().coerceIn(0, 100)
        sendCommand(
            mapOf(
                "req0__sc" to "setVolume",
                "req0_volume" to volInt.toString()
            )
        )
        mainHandler.post { listener.onVolumeChanged(volume) }
    }

    override fun getPositionMs(): Long = currentPositionMs
    override fun getDurationMs(): Long = durationMs
    override fun getPlayerState(): CastPlayerState = playerState

    private fun sendCommand(params: Map<String, String>) {
        val token = activeLoungeToken ?: return
        Thread {
            try {
                val formBuilder = FormBody.Builder()
                    .add("count", "1")
                    .add("ofs", reqOffset.getAndIncrement().toString())

                params.forEach { (k, v) -> formBuilder.add(k, v) }

                val url = "$LOUNGE_BIND_URL?device=REMOTE&id=$sessionId&VER=8&CVER=1"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-YouTube-LoungeId-Token", token)
                    .post(formBuilder.build())
                    .build()

                client.newCall(request).execute().close()
            } catch (ignored: Exception) {
                // Ignore transient network errors when sending lounge command
            }
        }.start()
    }

    private fun resolveLoungeTokenFromDial(): String? {
        val dialAppUrl = device.dialAppUrl ?: return null
        val youtubeAppUrl = "${dialAppUrl}YouTube"

        var screenId: String? = null
        try {
            val statusRequest = Request.Builder().url(youtubeAppUrl).build()
            val statusResponse = client.newCall(statusRequest).execute()
            val statusBody = statusResponse.body?.string() ?: ""
            statusResponse.close()

            val state = Regex("<state>(.*?)</state>").find(statusBody)?.groupValues?.get(1)
            screenId = Regex("<screenId>(.*?)</screenId>").find(statusBody)?.groupValues?.get(1)

            if (screenId.isNullOrEmpty() || state?.equals("running", ignoreCase = true) != true) {
                val launchRequest = Request.Builder()
                    .url(youtubeAppUrl)
                    .post(FormBody.Builder().build())
                    .build()
                client.newCall(launchRequest).execute().close()

                for (i in 1..8) {
                    Thread.sleep(500)
                    val pollRequest = Request.Builder().url(youtubeAppUrl).build()
                    val pollResponse = client.newCall(pollRequest).execute()
                    val pollBody = pollResponse.body?.string() ?: ""
                    pollResponse.close()
                    val pollScreenId = Regex("<screenId>(.*?)</screenId>").find(pollBody)?.groupValues?.get(1)
                    if (!pollScreenId.isNullOrEmpty()) {
                        screenId = pollScreenId
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
            // Ignore DIAL query errors, fallback to screenId resolution
        }

        if (screenId.isNullOrEmpty()) {
            return null
        }

        return fetchLoungeTokenForScreenId(screenId)
    }

    private fun fetchLoungeTokenForScreenId(screenId: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("screen_ids", screenId)
                .build()
            val request = Request.Builder()
                .url("https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch")
                .post(formBody)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val json = org.json.JSONObject(body)
            val screens = json.optJSONArray("screens") ?: return null
            if (screens.length() > 0) {
                screens.getJSONObject(0).optString("loungeToken")
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    private fun extractYouTubeVideoId(url: String): String? {
        // Match standard watch?v= or youtu.be/ or direct video IDs
        val vParam = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
        if (vParam != null) return vParam

        val shortUrl = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
        if (shortUrl != null) return shortUrl

        // If 11 chars alphanumeric
        if (url.length == 11 && url.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return url
        }
        return null
    }
}
