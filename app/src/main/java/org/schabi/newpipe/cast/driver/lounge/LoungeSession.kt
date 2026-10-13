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
import org.schabi.newpipe.cast.util.CastUtils

class LoungeSession(
    override val device: CastDevice,
    private val listener: CastStateListener,
    private val client: OkHttpClient = OkHttpClient()
) : CastSession {

    private val isConnected = AtomicBoolean(false)
    private val rid = AtomicInteger(1)
    private val sessionId = UUID.randomUUID().toString()
    private var activeLoungeToken: String? = device.loungeToken
    private var sid: String? = null
    private var gsessionId: String? = null

    private var currentPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var playerState = CastPlayerState.IDLE

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val LOUNGE_BIND_URL = "https://www.youtube.com/api/lounge/bc/bind"
        private const val ORIGIN_YOUTUBE = "https://www.youtube.com"
        private const val HEADER_LOUNGE_TOKEN = "X-YouTube-LoungeId-Token"
    }

    override fun connect() {
        if (isConnected.get()) return

        Thread {
            try {
                if (activeLoungeToken.isNullOrEmpty()) {
                    activeLoungeToken = resolveLoungeTokenFromDial()
                }

                check(!activeLoungeToken.isNullOrEmpty()) {
                    "Unable to get Lounge token for ${device.name}"
                }

                // Perform initial Browser Channel bind handshake
                check(bind()) {
                    "Failed to bind Lounge session for ${device.name}"
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
        sid = null
        gsessionId = null
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
        val videoId = CastUtils.extractYouTubeVideoId(streamUrl) ?: return
        val startSeconds = (startPositionMs / 1000.0).toString()
        sendCommand(
            mapOf(
                "req0__sc" to "setPlaylist",
                "req0_videoId" to videoId,
                "req0_currentTime" to startSeconds,
                "req0_currentIndex" to "-1",
                "req0_audioOnly" to "false",
                "req0_listId" to ""
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
        val seconds = (positionMs / 1000.0).toString()
        sendCommand(mapOf("req0__sc" to "seekTo", "req0_newTime" to seconds))
        currentPositionMs = positionMs
        mainHandler.post { listener.onProgressUpdated(currentPositionMs, durationMs) }
    }

    override fun setVolume(volume: Float) {
        val volInt = (volume * 100).toInt().coerceIn(0, 100)
        sendCommand(mapOf("req0__sc" to "setVolume", "req0_volume" to volInt.toString()))
        mainHandler.post { listener.onVolumeChanged(volume) }
    }

    override fun getPositionMs(): Long = currentPositionMs
    override fun getDurationMs(): Long = durationMs
    override fun getPlayerState(): CastPlayerState = playerState

    private fun bind(): Boolean {
        val token = activeLoungeToken ?: return false
        return try {
            val bindUrl = "$LOUNGE_BIND_URL?RID=0&VER=8&CVER=1"
            val formBody = FormBody.Builder()
                .add("device", "REMOTE_CONTROL")
                .add("id", sessionId)
                .add("name", "NewPipe")
                .add("mdx-version", "3")
                .add("pairing_type", "cast")
                .add("app", "android-phone-13.14.55")
                .build()

            val request = Request.Builder()
                .url(bindUrl)
                .addHeader(HEADER_LOUNGE_TOKEN, token)
                .addHeader("Origin", ORIGIN_YOUTUBE)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val sidMatch = Regex(""""c","(.*?)",""").find(body)
            val gsessionMatch = Regex(""""S","(.*?)"]""").find(body)

            if (sidMatch != null && gsessionMatch != null) {
                this.sid = sidMatch.groupValues[1]
                this.gsessionId = gsessionMatch.groupValues[1]
                this.rid.set(1)
                true
            } else {
                false
            }
        } catch (ignored: Exception) {
            false
        }
    }

    private fun sendCommand(params: Map<String, String>, retryOnFailure: Boolean = true) {
        val token = activeLoungeToken ?: return
        Thread {
            try {
                if ((sid.isNullOrEmpty() || gsessionId.isNullOrEmpty()) && !bind()) {
                    return@Thread
                }

                val formBuilder = FormBody.Builder().add("count", "1")
                params.forEach { (k, v) -> formBuilder.add(k, v) }

                val currentRid = rid.getAndIncrement()
                val url = "$LOUNGE_BIND_URL?SID=$sid&gsessionid=$gsessionId" +
                    "&RID=$currentRid&VER=8&CVER=1"
                val request = Request.Builder()
                    .url(url)
                    .addHeader(HEADER_LOUNGE_TOKEN, token)
                    .addHeader("Origin", ORIGIN_YOUTUBE)
                    .post(formBuilder.build())
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                response.close()

                if ((code == 400 || code == 404) && retryOnFailure && bind()) {
                    // Session expired or out of sync; re-bind and retry command
                    sendCommand(params, retryOnFailure = false)
                }
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
                    val pollScreenId = Regex("<screenId>(.*?)</screenId>")
                        .find(pollBody)?.groupValues?.get(1)
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
}
