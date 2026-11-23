/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download.artwork

import android.content.Context
import android.graphics.BitmapFactory
import androidx.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.image.ExtractorImageCompat

class DownloadArtworkRequest(val format: String, val imageUrls: ArrayList<String>) : Serializable {
    @JvmField var completed = false
    var capturedSourceCover = false
    var sourceCover: AudioArtwork? = null

    @Transient @Volatile
    var activeCall: okhttp3.Call? = null

    fun cancelFetch() {
        activeCall?.cancel()
    }

    companion object {
        const val PREFERENCE = "download_embed_audio_artwork"
        private const val serialVersionUID = 1L

        @JvmStatic
        fun create(context: Context, kind: Char, filename: String?, info: StreamInfo): DownloadArtworkRequest? {
            if (kind != 'a' || !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE, false)) return null
            val format = filename.orEmpty().substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            if (format != "mp3" && format != "m4a") return null
            // Download artwork is an explicit choice, separate from small/disabled list thumbnails.
            val images = ExtractorImageCompat.thumbnailImages(info)
                .sortedByDescending { image -> image.width.coerceAtLeast(0).toLong() * image.height.coerceAtLeast(0) }
                .map { it.url }.filter { it.startsWith("https://") || it.startsWith("http://") }.distinct().take(6)
            return DownloadArtworkRequest(format, ArrayList(images))
        }
    }
}

internal object DownloadArtworkImages {
    private const val MAX_PIXELS = 40_000_000L

    fun dimensions(artwork: AudioArtwork): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(artwork.bytes, 0, artwork.bytes.size, options)
        if (options.outMimeType != artwork.mimeType || options.outWidth <= 0 || options.outHeight <= 0 ||
            options.outWidth.toLong() * options.outHeight > MAX_PIXELS
        ) {
            return null
        }
        val sample = BitmapFactory.Options().apply { inSampleSize = (maxOf(options.outWidth, options.outHeight) / 128).coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeByteArray(artwork.bytes, 0, artwork.bytes.size, sample) ?: return null
        decoded.recycle()
        return options.outWidth to options.outHeight
    }

    /** Try advertised candidates, compare actual dimensions, and retain original encoded bytes. */
    fun fetch(request: DownloadArtworkRequest, cancelled: () -> Boolean): AudioArtwork? {
        if (request.imageUrls.isEmpty()) return null
        val client = runCatching { DownloaderImpl.getInstance().client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }.getOrNull() ?: return null
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(24)
        return chooseBest(request.imageUrls, { cancelled() || System.nanoTime() >= deadline }) { url ->
            runCatching {
                val call = client.newCall(Request.Builder().url(url).header("User-Agent", DownloaderImpl.USER_AGENT).build())
                request.activeCall = call
                if (cancelled()) call.cancel()
                call.execute().use response@{ response ->
                    if (!response.isSuccessful) return@response null
                    val body = response.body ?: return@response null
                    if (body.contentLength() > AudioArtwork.MAX_BYTES) return@response null
                    val bytes = body.byteStream().use bytes@{ input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (cancelled()) return@bytes null
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= AudioArtwork.MAX_BYTES)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: return@response null
                    val mime = when {
                        bytes.size >= 3 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 255 == 216 && bytes[2].toInt() and 255 == 255 -> "image/jpeg"
                        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
                        else -> return@response null
                    }
                    AudioArtwork(bytes, mime)
                }
            }.also { request.activeCall = null }.getOrNull()
        }
    }

    internal fun chooseBest(urls: List<String>, cancelled: () -> Boolean, load: (String) -> AudioArtwork?): AudioArtwork? {
        var best: AudioArtwork? = null
        var bestPixels = 0L
        for (url in urls.take(6)) {
            if (cancelled()) break
            val image = runCatching { load(url) }.getOrNull() ?: continue
            val dimensions = dimensions(image) ?: continue
            val pixels = dimensions.first.toLong() * dimensions.second
            if (pixels > bestPixels) {
                best = image
                bestPixels = pixels
            }
        }
        return best
    }
}
