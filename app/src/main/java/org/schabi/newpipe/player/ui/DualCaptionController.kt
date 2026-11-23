package org.schabi.newpipe.player.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadedStreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper

/**
 * Renders an optional second timed caption track without altering Media3's primary renderer.
 * The primary cue view moves to the top only when a usable secondary track is loaded.
 */
@UnstableApi
class DualCaptionController internal constructor(
    private val player: Player,
    private val content: RelativeLayout,
    private val primary: SubtitleView,
    private val scope: CoroutineScope,
    loadSubtitle: (suspend (SubtitlesStream) -> Subtitle)?
) {
    constructor(player: Player, content: RelativeLayout, primary: SubtitleView) : this(
        player,
        content,
        primary,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        null
    )

    private val context: Context = content.context
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val subtitleLoader = loadSubtitle
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
    private var requestedStream: SubtitlesStream? = null
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
        // MAIN/POPUP replacement can reuse the engine and its current metadata without another
        // metadata event. Restore the secondary track when the new UI attaches to that item.
        player.currentStreamInfo.ifPresent(::onMetadata)
        if (!player.exoPlayerIsNull()) renderPrimary(player.exoPlayer.currentCues.cues)
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
        requestedStream = null
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
                selectLanguage(if (index == 0) null else options[index - 1].languageTag)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun selectLanguage(language: String?) {
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
        loadSelected(reload = true)
    }

    fun onPrimaryCaptionChanged() {
        loadSelected()
    }

    fun onPrimaryCaptionSelected(language: String?) {
        player.setCaptionPreference(language)
        onPrimaryCaptionChanged()
    }

    private fun loadSelected(reload: Boolean = false) {
        val info = streamInfo ?: return
        val preferred = prefs.getString(PREF_LANGUAGE, null)
        val candidate = preferred?.let { DualCaptionSelection.select(info.subtitles.orEmpty(), it) }
            ?.takeUnless { stream ->
                val primaryLanguage = player.captionPreference ?: return@takeUnless false
                primaryLanguage.equals(stream.languageTag, ignoreCase = true) ||
                    PlayerHelper.captionLanguageStemOf(primaryLanguage).equals(
                        PlayerHelper.captionLanguageStemOf(PlayerHelper.captionLanguageOf(context, stream)),
                        ignoreCase = true
                    )
            }
        // Track callbacks also arrive for video/audio adaptation. Keep a loaded or pending
        // secondary track until its effective selection changes instead of downloading it again.
        if (!reload && candidate === requestedStream) return
        epoch++
        val generation = epoch
        request?.cancel()
        request = null
        requestedStream = candidate
        subtitle = null
        secondary.setCues(emptyList())
        renderPrimary(latestPrimary)
        if (candidate == null) return
        val identity = (info as? DownloadedStreamInfo)?.captionIdentities?.get(candidate.content)
        request = scope.launch {
            try {
                val parsed = subtitleLoader?.invoke(candidate)
                    ?: SecondaryCaptionLoader.load(context, candidate, identity).await()
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
            } finally {
                if (generation == epoch) request = null
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
        val timeUs = player.subtitleTiming.shift(positionMs.toLong().coerceAtLeast(0L) * 1_000L)
        secondary.setCues(active.getCues(timeUs))
    }

    private companion object {
        const val TAG = "DualCaptionController"
        const val PREF_LANGUAGE = "dual_captions_secondary_language"
    }
}

internal object DualCaptionSelection {
    fun select(streams: List<SubtitlesStream>, language: String): SubtitlesStream? {
        return streams.firstOrNull { it.languageTag == language && !it.isAutoGenerated }
            ?: streams.firstOrNull { it.languageTag == language }
    }
}
