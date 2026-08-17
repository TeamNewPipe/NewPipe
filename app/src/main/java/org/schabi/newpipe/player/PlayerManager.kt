package org.schabi.newpipe.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import okio.Buffer

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

@SuppressLint("StaticFieldLeak")
object PlayerManager {
    var player: ExoPlayer? = null
        private set
        
    private val _currentStreamInfo = MutableStateFlow<StreamInfo?>(null)
    val currentStreamInfo: StateFlow<StreamInfo?> = _currentStreamInfo.asStateFlow()
    
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (player == null) {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 20_000,
                    /* maxBufferMs = */ 60_000,
                    /* bufferForPlaybackMs = */ 250,
                    /* bufferForPlaybackAfterRebufferMs = */ 500
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
        return player!!
    }
    
    fun setCurrentVideo(url: String, info: StreamInfo, context: Context? = null) {
        _currentUrl.value = url
        _currentStreamInfo.value = info

        if (context != null) {
            scope.launch {
                val thumbnailUrl = withContext(Dispatchers.Default) {
                    ImageStrategy.choosePreferredImage(info.thumbnails, PreferredImageQuality.HIGH)
                }
                val artworkUri = (thumbnailUrl ?: "").toUri()

                var artworkData: ByteArray? = null
                if (!thumbnailUrl.isNullOrEmpty()) {
                    try {
                        val request = ImageRequest.Builder(context.applicationContext)
                            .data(thumbnailUrl)
                            .build()
                        val result = context.applicationContext.imageLoader.execute(request)
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            artworkData = withContext(Dispatchers.Default) {
                                val buffer = Buffer()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
                                buffer.readByteArray()
                            }
                        }
                    } catch (_: Exception) {}
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(info.name ?: "Unknown")
                    .setArtist(info.uploaderName ?: "Unknown")
                    .setArtworkUri(artworkUri)
                    .apply {
                        if (artworkData != null) {
                            setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .setIsPlayable(true)
                    .build()

                withContext(Dispatchers.Main.immediate) {
                    player?.playlistMetadata = metadata
                }
            }
        }
    }
    
    fun clearVideo() {
        player?.stop()
        player?.clearMediaItems()
        _currentUrl.value = null
        _currentStreamInfo.value = null
    }
}

