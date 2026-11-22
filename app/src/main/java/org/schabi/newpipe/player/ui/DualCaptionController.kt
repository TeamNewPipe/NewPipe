package org.schabi.newpipe.player.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper

/**
 * Renders an optional second timed caption track without altering Media3's primary renderer.
 * The primary cue view moves to the top only when a usable secondary track is loaded.
 */
@UnstableApi
class DualCaptionController(
    private val player: Player,
    private val content: RelativeLayout,
    private val primary: SubtitleView
) {
    private val context: Context = content.context
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val secondary = SubtitleView(context).apply {
        setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * PlayerHelper.getCaptionScale(context)
        )
        setStyle(PlayerHelper.getCaptionStyle(context))
        setApplyEmbeddedStyles(false)
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var streamInfo: StreamInfo? = null
    private var subtitle: Subtitle? = null
    private var request: Job? = null
    private var epoch = 0
    private var latestPrimary: List<Cue> = emptyList()

    init {
        val index = content.indexOfChild(primary)
        content.addView(
            secondary,
            if (index < 0) content.childCount else index + 1,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        secondary.setCues(emptyList())
    }

    fun onMetadata(info: StreamInfo) {
        clear()
        streamInfo = info
        loadSelected()
    }

    fun clear() {
        epoch++
        request?.cancel()
        request = null
        streamInfo = null
        subtitle = null
        latestPrimary = emptyList()
        secondary.setCues(emptyList())
        primary.setCues(emptyList())
    }

    fun release() {
        clear()
        scope.cancel()
        content.removeView(secondary)
    }

    fun showLanguagePicker() {
        val info = streamInfo ?: return
        val options = info.subtitles.orEmpty().filter {
            it.format?.mimeType != null && it.content.isNotBlank()
        }
        if (options.isEmpty()) return
        val labels = listOf(context.getString(R.string.dual_captions_off)) +
            options.map { PlayerHelper.captionLanguageOf(context, it) }
        val preference = prefs.getString(PREF_LANGUAGE, null)
        val selected = options.indexOfFirst { it.languageTag == preference }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dual_captions_title)
            .setSingleChoiceItems(labels.toTypedArray(), selected + 1) { dialog, index ->
                prefs.edit().putString(
                    PREF_LANGUAGE,
                    if (index == 0) null else options[index - 1].languageTag
                ).apply()
                loadSelected()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadSelected() {
        epoch++
        val generation = epoch
        request?.cancel()
        subtitle = null
        secondary.setCues(emptyList())
        renderPrimary(latestPrimary)
        val info = streamInfo ?: return
        val preferred = prefs.getString(PREF_LANGUAGE, null) ?: return
        val candidate = DualCaptionSelection.select(info.subtitles.orEmpty(), preferred) ?: return
        // Do not render the same language twice when the primary track is explicitly selected.
        val currentPrimary = player.captionPreference.orEmpty()
        if (
            currentPrimary == candidate.languageTag ||
            currentPrimary == PlayerHelper.captionLanguageOf(context, candidate)
        ) {
            return
        }
        request = scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) { load(candidate) }
                if (generation == epoch) {
                    subtitle = parsed
                    renderPrimary(latestPrimary)
                    val position = if (player.exoPlayerIsNull()) {
                        0L
                    } else {
                        player.exoPlayer.currentPosition
                    }
                    onProgress(position.toInt().coerceAtLeast(0))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load secondary subtitle track", e)
            }
        }
    }

    fun renderPrimary(cues: List<Cue>) {
        latestPrimary = cues
        val displayed = if (subtitle == null) {
            cues
        } else {
            cues.map {
                it.buildUpon()
                    .setLine(0.09f, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START)
                    .build()
            }
        }
        primary.setCues(displayed)
    }

    fun onProgress(positionMs: Int) {
        val active = subtitle ?: return
        val timeUs = positionMs.toLong().coerceAtLeast(0L) * 1_000L
        secondary.setCues(active.getCues(timeUs))
    }

    private fun load(stream: SubtitlesStream): Subtitle {
        val mime = stream.format?.mimeType ?: throw IOException("Unsupported subtitle format")
        val format = Format.Builder()
            .setSampleMimeType(mime)
            .setLanguage(stream.languageTag)
            .build()
        val factory = DefaultSubtitleParserFactory()
        if (!factory.supportsFormat(format)) {
            throw IOException("Unsupported subtitle format: $mime")
        }
        val bytes = if (!stream.isUrl) {
            stream.content.toByteArray(Charsets.UTF_8)
        } else {
            val request = Request.Builder().url(stream.content).build()
            DownloaderImpl.getInstance().client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Subtitle HTTP ${response.code}")
                }
                val input = response.body?.byteStream() ?: throw IOException("Empty subtitle")
                input.use { streamIn ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = streamIn.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_BYTES) {
                            throw IOException("Subtitle too large")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            }
        }
        if (bytes.size > MAX_BYTES) {
            throw IOException("Subtitle too large")
        }
        return factory.create(format).parseToLegacySubtitle(bytes, 0, bytes.size)
    }

    private companion object {
        const val TAG = "DualCaptionController"
        const val PREF_LANGUAGE = "dual_captions_secondary_language"
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}

internal object DualCaptionSelection {
    fun select(streams: List<SubtitlesStream>, language: String): SubtitlesStream? {
        return streams.firstOrNull { it.languageTag == language && !it.isAutoGenerated }
            ?: streams.firstOrNull { it.languageTag == language }
    }
}
