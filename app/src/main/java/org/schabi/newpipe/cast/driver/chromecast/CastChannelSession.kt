package org.schabi.newpipe.cast.driver.chromecast

import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastPlayerState
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastStateListener
import org.schabi.newpipe.cast.util.CastUtils

class CastChannelSession(
    override val device: CastDevice,
    private val listener: CastStateListener
) : CastSession {

    private val isConnected = AtomicBoolean(false)
    private val requestId = AtomicInteger(1)
    private var sslSocket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var activeAppId: String? = null
    private var transportId: String? = null
    private var mediaSessionId: Long = 0L
    private var currentPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var playerState = CastPlayerState.IDLE

    private val mainHandler = Handler(Looper.getMainLooper())
    private var readThread: Thread? = null
    private var heartbeatThread: Thread? = null
    private var pendingMediaLoad: MediaLoadRequest? = null

    private data class MediaLoadRequest(
        val streamUrl: String,
        val title: String,
        val artist: String?,
        val thumbnailUrl: String?,
        val startPositionMs: Long,
        val youtubeVideoId: String? = null
    )

    companion object {
        private const val SENDER_ID = "sender-0"
        private const val RECEIVER_ID = "receiver-0"
        private const val APP_ID_DEFAULT_MEDIA_RECEIVER = "CC1AD845"
        private const val APP_ID_YOUTUBE = "233637DE"

        private const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        private const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        private const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NS_YOUTUBE_MDX = "urn:x-cast:com.google.youtube.mdx"
    }

    override fun connect() {
        if (isConnected.get()) return

        Thread {
            try {
                val host = device.hostAddress?.hostAddress ?: return@Thread

                @Suppress("kotlin:S4830")
                val trustManager = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>?,
                        authType: String?
                    ) {
                        // CastV2 client auth not required
                    }

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>?,
                        authType: String?
                    ) {
                        if (chain.isNullOrEmpty()) {
                            throw CertificateException("Missing Chromecast certificate chain")
                        }
                        for (cert in chain) {
                            cert.checkValidity()
                        }
                    }
                }
                val trustAllCerts = arrayOf<TrustManager>(trustManager)

                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val socket = sslContext.socketFactory.createSocket(host, device.port) as SSLSocket
                socket.soTimeout = 30000

                sslSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                isConnected.set(true)

                startMessageListener()
                startHeartbeat()

                // Step 1: Connect to receiver-0
                sendMessage(RECEIVER_ID, NS_CONNECTION, JSONObject().put("type", "CONNECT"))

                // Step 2: Launch YouTube Receiver app by default
                launchApp(APP_ID_YOUTUBE)
            } catch (e: Exception) {
                disconnect()
                mainHandler.post { listener.onSessionEnded(this, e) }
            }
        }.start()
    }

    private fun launchApp(appId: String) {
        val launchPayload = JSONObject().apply {
            put("type", "LAUNCH")
            put("appId", appId)
            put("requestId", nextRequestId())
        }
        sendMessage(RECEIVER_ID, NS_RECEIVER, launchPayload)
    }

    override fun disconnect() {
        if (!isConnected.getAndSet(false)) return

        Thread {
            try {
                transportId?.let { tid ->
                    sendMessage(tid, NS_CONNECTION, JSONObject().put("type", "CLOSE"))
                }
                sendMessage(RECEIVER_ID, NS_CONNECTION, JSONObject().put("type", "CLOSE"))
                sslSocket?.close()
            } catch (ignored: Exception) {
                // Ignore socket close errors during session shutdown
            } finally {
                sslSocket = null
                inputStream = null
                outputStream = null
                readThread?.interrupt()
                heartbeatThread?.interrupt()
                mainHandler.post { listener.onSessionEnded(this, null) }
            }
        }.start()
    }

    override fun isConnected(): Boolean = isConnected.get()

    override fun loadMedia(
        streamUrl: String,
        title: String,
        artist: String?,
        thumbnailUrl: String?,
        startPositionMs: Long
    ) {
        val ytVideoId = CastUtils.extractYouTubeVideoId(streamUrl)
        val request = MediaLoadRequest(
            streamUrl,
            title,
            artist,
            thumbnailUrl,
            startPositionMs,
            ytVideoId
        )
        val targetAppId = if (ytVideoId != null) {
            APP_ID_YOUTUBE
        } else {
            APP_ID_DEFAULT_MEDIA_RECEIVER
        }

        val tid = transportId
        if (tid == null || activeAppId != targetAppId) {
            pendingMediaLoad = request
            launchApp(targetAppId)
            return
        }

        sendMediaRequest(tid, request)
    }

    private fun sendMediaRequest(tid: String, request: MediaLoadRequest) {
        Thread {
            try {
                if (request.youtubeVideoId != null) {
                    val flingPayload = JSONObject().apply {
                        put("type", "flingVideo")
                        put(
                            "data",
                            JSONObject().apply {
                                put("videoId", request.youtubeVideoId)
                                put("currentTime", request.startPositionMs / 1000.0)
                            }
                        )
                    }
                    sendMessage(tid, NS_YOUTUBE_MDX, flingPayload)
                    currentPositionMs = request.startPositionMs
                    playerState = CastPlayerState.PLAYING
                    mainHandler.post {
                        listener.onPlaybackStateChanged(playerState)
                        listener.onProgressUpdated(currentPositionMs, durationMs)
                    }
                } else {
                    sendDefaultMediaLoad(tid, request)
                }
            } catch (ignored: Exception) {
                // Ignore send errors during session teardown
            }
        }.start()
    }

    private fun sendDefaultMediaLoad(tid: String, request: MediaLoadRequest) {
        val mediaJson = JSONObject().apply {
            put("contentId", request.streamUrl)
            put("streamType", "BUFFERED")
            val isHls = request.streamUrl.contains(".m3u8")
            put("contentType", if (isHls) "application/x-mpegURL" else "video/mp4")
            val metadata = JSONObject().apply {
                put("metadataType", 0)
                put("title", request.title)
                request.artist?.let { put("subtitle", it) }
                request.thumbnailUrl?.let {
                    put("images", JSONArray().put(JSONObject().put("url", it)))
                }
            }
            put("metadata", metadata)
        }

        val payload = JSONObject().apply {
            put("type", "LOAD")
            put("requestId", nextRequestId())
            put("autoplay", true)
            put("currentTime", request.startPositionMs / 1000.0)
            put("media", mediaJson)
        }
        sendMessage(tid, NS_MEDIA, payload)
    }

    override fun play() {
        sendMediaCommand("PLAY")
        playerState = CastPlayerState.PLAYING
        mainHandler.post { listener.onPlaybackStateChanged(playerState) }
    }

    override fun pause() {
        sendMediaCommand("PAUSE")
        playerState = CastPlayerState.PAUSED
        mainHandler.post { listener.onPlaybackStateChanged(playerState) }
    }

    override fun seekTo(positionMs: Long) {
        val tid = transportId ?: return
        Thread {
            try {
                if (activeAppId == APP_ID_YOUTUBE) {
                    val pendingYtId = pendingMediaLoad?.youtubeVideoId
                    if (pendingYtId != null) {
                        val seekPayload = JSONObject().apply {
                            put("type", "flingVideo")
                            put(
                                "data",
                                JSONObject().apply {
                                    put("videoId", pendingYtId)
                                    put("currentTime", positionMs / 1000.0)
                                }
                            )
                        }
                        sendMessage(tid, NS_YOUTUBE_MDX, seekPayload)
                    }
                }
                if (mediaSessionId > 0) {
                    val payload = JSONObject().apply {
                        put("type", "SEEK")
                        put("requestId", nextRequestId())
                        put("mediaSessionId", mediaSessionId)
                        put("currentTime", positionMs / 1000.0)
                    }
                    sendMessage(tid, NS_MEDIA, payload)
                }
                currentPositionMs = positionMs
                mainHandler.post { listener.onProgressUpdated(currentPositionMs, durationMs) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun setVolume(volume: Float) {
        val tid = transportId ?: return
        Thread {
            try {
                val volumeJson = JSONObject().apply {
                    put("level", volume.toDouble().coerceIn(0.0, 1.0))
                }
                val payload = JSONObject().apply {
                    put("type", "SET_VOLUME")
                    put("requestId", nextRequestId())
                    put("volume", volumeJson)
                }
                sendMessage(tid, NS_RECEIVER, payload)
                mainHandler.post { listener.onVolumeChanged(volume) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun getPositionMs(): Long = currentPositionMs
    override fun getDurationMs(): Long = durationMs
    override fun getPlayerState(): CastPlayerState = playerState

    private fun sendMediaCommand(type: String) {
        val tid = transportId ?: return
        Thread {
            try {
                if (mediaSessionId > 0) {
                    val payload = JSONObject().apply {
                        put("type", type)
                        put("requestId", nextRequestId())
                        put("mediaSessionId", mediaSessionId)
                    }
                    sendMessage(tid, NS_MEDIA, payload)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun sendMessage(destinationId: String, namespace: String, payload: JSONObject) {
        val out = outputStream ?: return
        val message = CastMessage(
            sourceId = SENDER_ID,
            destinationId = destinationId,
            namespace = namespace,
            payloadUtf8 = payload.toString()
        )
        synchronized(this) {
            CastMessageWire.writeMessage(out, message)
        }
    }

    private fun startMessageListener() {
        val stream = inputStream ?: return
        readThread = Thread {
            try {
                while (isConnected.get()) {
                    val message = CastMessageWire.readMessage(stream) ?: break
                    handleIncomingMessage(message)
                }
            } catch (ignored: Exception) {
                // Socket closed or EOF reached
            } finally {
                disconnect()
            }
        }.also { it.start() }
    }

    private fun handleIncomingMessage(message: CastMessage) {
        try {
            val json = JSONObject(message.payloadUtf8)
            when (json.optString("type")) {
                "RECEIVER_STATUS" -> handleReceiverStatus(json)

                "MEDIA_STATUS" -> handleMediaStatus(json)

                "PING" -> {
                    sendMessage(message.sourceId, NS_HEARTBEAT, JSONObject().put("type", "PONG"))
                }
            }
        } catch (ignored: Exception) {
            // Ignore malformed payloads from network
        }
    }

    private fun handleReceiverStatus(json: JSONObject) {
        val status = json.optJSONObject("status") ?: return
        val applications = status.optJSONArray("applications") ?: return
        if (applications.length() == 0) return

        val app = applications.optJSONObject(0) ?: return
        val appId = app.optString("appId")
        val tid = app.optString("transportId")
        if (tid.isNotEmpty() && tid != transportId) {
            val isFirstConnect = (transportId == null)
            activeAppId = appId
            transportId = tid
            sendMessage(tid, NS_CONNECTION, JSONObject().put("type", "CONNECT"))
            if (appId == APP_ID_YOUTUBE) {
                val mdxQuery = JSONObject().put("type", "getMdxSessionStatus")
                sendMessage(tid, NS_YOUTUBE_MDX, mdxQuery)
            }
            if (isFirstConnect) {
                mainHandler.post { listener.onSessionStarted(this) }
            }
            pendingMediaLoad?.let { req ->
                pendingMediaLoad = null
                sendMediaRequest(tid, req)
            }
        }
    }

    private fun handleMediaStatus(json: JSONObject) {
        val statusArray = json.optJSONArray("status") ?: return
        if (statusArray.length() == 0) return

        val mediaStatus = statusArray.optJSONObject(0) ?: return
        mediaSessionId = mediaStatus.optLong("mediaSessionId", mediaSessionId)
        val currentTimeSec = mediaStatus.optDouble("currentTime", 0.0)
        currentPositionMs = (currentTimeSec * 1000).toLong()

        mediaStatus.optJSONObject("media")?.let { mediaObj ->
            val durationSec = mediaObj.optDouble("duration", 0.0)
            durationMs = (durationSec * 1000).toLong()
        }

        playerState = parsePlayerState(mediaStatus.optString("playerState"))
        val idleReason = mediaStatus.optString("idleReason")

        mainHandler.post {
            listener.onPlaybackStateChanged(playerState)
            listener.onProgressUpdated(currentPositionMs, durationMs)
            if (idleReason == "FINISHED") {
                listener.onMediaCompleted()
            }
        }
    }

    private fun parsePlayerState(stateStr: String): CastPlayerState = when (stateStr) {
        "PLAYING" -> CastPlayerState.PLAYING
        "PAUSED" -> CastPlayerState.PAUSED
        "BUFFERING" -> CastPlayerState.BUFFERING
        else -> CastPlayerState.IDLE
    }

    private fun startHeartbeat() {
        heartbeatThread = Thread {
            try {
                while (isConnected.get()) {
                    Thread.sleep(5000)
                    if (!isConnected.get()) break
                    sendMessage(RECEIVER_ID, NS_HEARTBEAT, JSONObject().put("type", "PING"))
                }
            } catch (ignored: InterruptedException) {
                // Heartbeat thread interrupted on disconnect
            }
        }.also { it.start() }
    }

    private fun nextRequestId(): Int = requestId.getAndIncrement()
}
