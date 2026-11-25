package org.schabi.newpipe.player.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.Subtitle
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.ceil
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
    private var languagePicker: AlertDialog? = null
    private var engine: Media3Player? = null
    private var released = false
    private var lastPositionMs = 0L
    private val boundaryHandler = Handler(Looper.getMainLooper())
    private val boundaryUpdate = Runnable { updateSecondary() }
    private val timingChanged: () -> Unit = {
        if (Looper.myLooper() == boundaryHandler.looper) {
            updateSecondary()
        } else {
            boundaryHandler.post(boundaryUpdate)
        }
    }
    private var playbackListener: Media3Player.Listener? = null

    private fun listenerFor(playback: Media3Player) = object : Media3Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (engine !== playback || released) return
            // Clear before the next item's metadata loads, even if the queue index is reused.
            if (reason != Media3Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) clear()
        }

        override fun onEvents(player: Media3Player, events: Media3Player.Events) {
            if (engine !== player || released) return
            // Cached/duplicate queue items may not emit another metadata callback. The owner's
            // earlier onEvents listener has now settled metadata for the new media item.
            if (streamInfo == null && events.contains(Media3Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                this@DualCaptionController.player.currentStreamInfo.ifPresent(::onMetadata)
            }
            if (
                events.containsAny(
                    Media3Player.EVENT_POSITION_DISCONTINUITY,
                    Media3Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Media3Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Media3Player.EVENT_IS_PLAYING_CHANGED,
                    Media3Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Media3Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                    Media3Player.EVENT_TIMELINE_CHANGED,
                    Media3Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Media3Player.EVENT_TRACKS_CHANGED
                )
            ) {
                updateSecondary()
            }
        }
    }

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
        player.subtitleTiming.addOffsetListener(timingChanged)
        // MAIN/POPUP replacement can reuse the engine and its current metadata without another
        // metadata event. Restore the secondary track when the new UI attaches to that item.
        attachPlayer()
    }

    fun attachPlayer() {
        if (released) return
        val next = if (player.exoPlayerIsNull()) null else player.exoPlayer
        if (engine !== next) {
            boundaryHandler.removeCallbacks(boundaryUpdate)
            playbackListener?.let { engine?.removeListener(it) }
            engine = next
            playbackListener = next?.let(::listenerFor)
            playbackListener?.let { next?.addListener(it) }
        }
        if (streamInfo == null) player.currentStreamInfo.ifPresent(::onMetadata)
        if (next != null) renderPrimary(next.currentCues.cues)
        updateSecondary()
    }

    fun detachPlayer() {
        playbackListener?.let { engine?.removeListener(it) }
        playbackListener = null
        engine = null
        clear()
    }

    fun onMetadata(info: StreamInfo) {
        if (released) return
        clear()
        streamInfo = info
        loadSelected()
    }

    fun clear() {
        epoch++
        dismissLanguagePicker()
        boundaryHandler.removeCallbacks(boundaryUpdate)
        request?.cancel()
        request = null
        streamInfo = null
        requestedStream = null
        subtitle = null
        lastPositionMs = 0L
        latestPrimary = emptyList()
        secondary.setCues(emptyList())
        primary.setCues(emptyList())
    }

    fun release() {
        if (released) return
        released = true
        detachPlayer()
        player.subtitleTiming.removeOffsetListener(timingChanged)
        scope.cancel()
        content.removeView(secondary)
    }

    fun showLanguagePicker() {
        dismissLanguagePicker()
        if (released || !content.isAttachedToWindow || content.windowToken == null) return
        val info = streamInfo ?: return
        val options = info.subtitles.orEmpty().filter {
            it.format?.mimeType != null && it.content.isNotBlank()
        }
        if (options.isEmpty()) return
        val labels = listOf(context.getString(R.string.dual_captions_off)) +
            options.map { PlayerHelper.captionLanguageOf(context, it) }
        val preference = prefs.getString(PREF_LANGUAGE, null)
        val selected = options.indexOfFirst { it.languageTag == preference }
        val dialog = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(content))
            .setTitle(R.string.dual_captions_title)
            .setSingleChoiceItems(labels.toTypedArray(), selected + 1) { dialog, index ->
                // An old window must not apply a choice after replacement or a media transition.
                if (
                    languagePicker === dialog && languagePicker?.isShowing == true &&
                    streamInfo === info && !released &&
                    index in labels.indices
                ) {
                    selectLanguage(if (index == 0) null else options[index - 1].languageTag)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        languagePicker = dialog
        AnchoredPlaybackDialog.show(content, dialog) {
            if (languagePicker === dialog) languagePicker = null
        }
        if (!dialog.isShowing && languagePicker === dialog) languagePicker = null
    }

    private fun dismissLanguagePicker() {
        val previous = languagePicker
        languagePicker = null
        previous?.dismiss()
    }

    internal fun selectLanguage(language: String?) {
        if (released) return
        prefs.edit().putString(PREF_LANGUAGE, language).apply()
        loadSelected(reload = true)
    }

    fun onPrimaryCaptionChanged() {
        if (released) return
        loadSelected()
        updateSecondary()
    }

    fun onPrimaryCaptionSelected(language: String?) {
        if (released) return
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
        boundaryHandler.removeCallbacks(boundaryUpdate)
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
                    updateSecondary()
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
        if (released) return
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
        updateSecondary(positionMs.toLong())
    }

    private fun updateSecondary(fallbackPositionMs: Long = lastPositionMs) {
        boundaryHandler.removeCallbacks(boundaryUpdate)
        if (released) return
        val active = subtitle ?: return
        val playback = engine
        lastPositionMs = (playback?.currentPosition ?: fallbackPositionMs).coerceAtLeast(0L)
        val timeUs = player.subtitleTiming.shift(lastPositionMs * 1_000L)
        secondary.setCues(active.getCues(timeUs))
        if (playback?.isPlaying != true) return

        val nextEvent = active.getNextEventTimeIndex(timeUs)
        if (nextEvent !in 0 until active.eventTimeCount) return
        val speed = playback.playbackParameters.speed
        if (!speed.isFinite() || speed <= 0f) return
        // Convert caption time to wall time at the current speed, rounding up so an early
        // millisecond callback cannot miss a cue. Re-read the engine clock when it fires.
        val delayMs = ceil((active.getEventTime(nextEvent) - timeUs) / (1_000.0 * speed))
            .toLong().coerceAtLeast(1L)
        boundaryHandler.postDelayed(boundaryUpdate, delayMs)
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
