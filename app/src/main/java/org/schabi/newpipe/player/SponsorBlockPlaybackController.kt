/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.widget.Toast
import androidx.media3.common.C
import kotlin.math.roundToLong
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockPlaybackDecision

/** Owns SponsorBlock playback state, skip decisions, and player UI updates. */
internal class SponsorBlockPlaybackController(private val player: Player) {
    private var segments: List<SponsorBlockSegment> = emptyList()
    private val skippedSegmentKeys = mutableSetOf<String>()
    private var ignoredSegmentKey: String? = null
    private var displayedManualSkipSegmentKey: String? = null
    private var skipInProgress = false

    private val categoryStateProvider =
        object : SponsorBlockPlaybackDecision.CategoryStateProvider {
            override fun isEnabled(category: SponsorBlockCategory): Boolean {
                return SponsorBlockCategoryRepository.isApiCategoryEnabled(
                    player.context,
                    category
                )
            }

            override fun getBehavior(category: SponsorBlockCategory): SponsorBlockBehavior {
                return SponsorBlockCategoryRepository.getBehavior(player.context, category)
            }
        }

    fun onProgress() {
        maybeSkipSegment()
        updateSeekBarMarkers()
    }

    fun remainingTimeMillis(durationMs: Long, positionMs: Long, speed: Float): Long? {
        val automaticSkips = if (isEnabled()) {
            segments.asSequence().filter { segment ->
                val category = segment.category
                segment.startTime.isFinite() && segment.endTime.isFinite() &&
                    segment.startTime >= 0 && segment.endTime > segment.startTime &&
                    segment.action == SponsorBlockAction.SKIP && category != null &&
                    category != SponsorBlockCategory.HIGHLIGHT && categoryStateProvider.isEnabled(category) &&
                    behavior(segment) == SponsorBlockBehavior.SKIP && isRunnableSegment(segment, SponsorBlockBehavior.SKIP)
            }.map { PlaybackTimeRemaining.Interval(startMillis(it), endMillis(it)) }.toList()
        } else {
            emptyList()
        }
        return PlaybackTimeRemaining.estimate(durationMs, positionMs, speed, automaticSkips)
    }

    fun onPositionDiscontinuity(isManualSeek: Boolean, positionMillis: Long) {
        if (isManualSeek && !skipInProgress) {
            ignoredSegmentKey = SponsorBlockPlaybackDecision.resolveIgnoredSegmentAfterManualSeek(
                activeActionableSegment(positionMillis)?.let(::segmentKey),
                player.prefs.getBoolean(
                    player.context.getString(R.string.sponsor_block_graced_rewind_key),
                    true
                ),
                skippedSegmentKeys
            )
        }
        if (skipInProgress) {
            skipInProgress = false
        }
    }

    fun updateSegments(info: StreamInfo) = updateSegments(info.sponsorBlockSegments?.toList().orEmpty())

    fun updateSegments(savedSegments: List<SponsorBlockSegment>) {
        skippedSegmentKeys.clear()
        ignoredSegmentKey = null
        hideManualSkipButton()
        skipInProgress = false
        if (!isEnabled()) {
            segments = emptyList()
            clearSeekBarMarkers()
            return
        }
        segments = savedSegments
        updateSeekBarMarkers()
    }

    fun reset() {
        skippedSegmentKeys.clear()
        ignoredSegmentKey = null
        segments = emptyList()
        skipInProgress = false
        hideManualSkipButton()
        clearSeekBarMarkers()
    }

    fun hideManualSkipButton() {
        if (displayedManualSkipSegmentKey == null) {
            return
        }
        displayedManualSkipSegmentKey = null
        player.UIs().call(PlayerUi::hideSponsorBlockSkipButton)
    }

    private fun maybeSkipSegment() {
        if (player.exoPlayerIsNull()) {
            hideManualSkipButton()
            return
        }
        val exoPlayer = player.exoPlayer
        if (
            !isEnabled() ||
            segments.isEmpty() ||
            !player.isPlaying ||
            exoPlayer.playbackState != androidx.media3.common.Player.STATE_READY
        ) {
            hideManualSkipButton()
            return
        }

        val currentPositionMillis = exoPlayer.currentPosition
        ignoredSegmentKey = SponsorBlockPlaybackDecision.resolveIgnoredSegmentForProgress(
            ignoredSegmentKey,
            segments,
            currentPositionMillis,
            SEGMENT_PROVIDER,
            ::segmentKey,
            categoryStateProvider
        )
        val activeSegment = SponsorBlockPlaybackDecision.findFirstRunnableSegment(
            segments,
            currentPositionMillis,
            SEGMENT_PROVIDER,
            categoryStateProvider
        ) { segment, behavior -> isRunnableSegment(segment, behavior) }

        if (activeSegment == null) {
            hideManualSkipButton()
            return
        }

        val key = segmentKey(activeSegment)
        val targetPositionMillis = segmentEndMillis(activeSegment)
        if (targetPositionMillis <= currentPositionMillis) {
            skippedSegmentKeys += key
            hideManualSkipButton()
            return
        }

        if (behavior(activeSegment) == SponsorBlockBehavior.MANUAL) {
            showManualSkipButton(activeSegment)
            return
        }
        hideManualSkipButton()
        skipSegment(activeSegment)
    }

    private fun isRunnableSegment(
        segment: SponsorBlockSegment,
        behavior: SponsorBlockBehavior
    ): Boolean {
        val key = segmentKey(segment)
        return key !in skippedSegmentKeys &&
            (behavior != SponsorBlockBehavior.SKIP || key != ignoredSegmentKey)
    }

    private fun skipSegment(segment: SponsorBlockSegment) {
        val key = segmentKey(segment)
        skipInProgress = true
        skippedSegmentKeys += key
        hideManualSkipButton()
        player.exoPlayer.seekTo(segmentEndMillis(segment))
        if (
            player.prefs.getBoolean(
                player.context.getString(R.string.sponsor_block_notifications_key),
                true
            )
        ) {
            Toast.makeText(
                player.context,
                player.context.getString(
                    R.string.sponsor_block_skipped_segment,
                    categoryName(segment)
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun segmentEndMillis(segment: SponsorBlockSegment): Long {
        val endMillis = endMillis(segment)
        val durationMillis = player.exoPlayer.duration
        return if (durationMillis > 0 && durationMillis != C.TIME_UNSET) {
            endMillis.coerceIn(0, durationMillis)
        } else {
            endMillis.coerceAtLeast(0)
        }
    }

    private fun showManualSkipButton(segment: SponsorBlockSegment) {
        if (!isCurrentStreamEligibleForUi()) {
            hideManualSkipButton()
            return
        }

        val key = segmentKey(segment)
        if (key == displayedManualSkipSegmentKey) {
            return
        }

        val label = if (segment.category == SponsorBlockCategory.SPONSOR) {
            player.context.getString(R.string.sponsor_block_skip_sponsor)
        } else {
            player.context.getString(R.string.sponsor_block_skip_segment)
        }
        displayedManualSkipSegmentKey = key
        player.UIs().call { ui ->
            ui.showSponsorBlockSkipButton(label) {
                if (!player.exoPlayerIsNull() && key !in skippedSegmentKeys) {
                    skipSegment(segment)
                }
            }
        }
    }

    private fun activeActionableSegment(positionMillis: Long): SponsorBlockSegment? {
        if (!isEnabled() || segments.isEmpty()) {
            return null
        }
        return SponsorBlockPlaybackDecision.findFirstActionableSegment(
            segments,
            positionMillis,
            SEGMENT_PROVIDER,
            categoryStateProvider
        )
    }

    private fun updateSeekBarMarkers() {
        if (!isEnabled() || segments.isEmpty() || !isCurrentStreamEligibleForUi()) {
            clearSeekBarMarkers()
            return
        }

        val markerSegments = segments.filter(::isValidMarkerSegment)
        if (markerSegments.isEmpty()) {
            clearSeekBarMarkers()
            return
        }
        player.UIs().call { ui ->
            ui.updateSponsorBlockSeekBarMarkers(markerSegments, player.exoPlayer.duration)
        }
    }

    private fun clearSeekBarMarkers() {
        player.UIs().call(PlayerUi::clearSponsorBlockSeekBarMarkers)
    }

    private fun isCurrentStreamEligibleForUi(): Boolean {
        if (player.exoPlayerIsNull()) {
            return false
        }
        val durationMillis = player.exoPlayer.duration
        if (durationMillis <= 0 || durationMillis == C.TIME_UNSET) {
            return false
        }
        return player.currentStreamInfo
            .map(StreamInfo::getStreamType)
            .map { it == StreamType.VIDEO_STREAM || it == StreamType.POST_LIVE_STREAM }
            .orElse(player.currentItem?.let { it.isLocalMedia && it.streamType == StreamType.VIDEO_STREAM } == true)
    }

    private fun isValidMarkerSegment(segment: SponsorBlockSegment): Boolean {
        return startMillis(segment) >= 0 &&
            endMillis(segment) > startMillis(segment) &&
            segment.category != null &&
            segment.action != null &&
            (
                segment.action == SponsorBlockAction.SKIP ||
                    segment.action == SponsorBlockAction.POI
                ) &&
            categoryStateProvider.isEnabled(segment.category)
    }

    private fun isEnabled(): Boolean {
        return player.prefs.getBoolean(
            player.context.getString(R.string.sponsor_block_enable_key),
            false
        )
    }

    private fun behavior(segment: SponsorBlockSegment): SponsorBlockBehavior {
        return SponsorBlockCategoryRepository.getBehavior(player.context, segment.category)
    }

    private fun segmentKey(segment: SponsorBlockSegment): String {
        val uuid = segment.uuid
        if (!uuid.isNullOrEmpty()) {
            return uuid
        }
        return "${segment.category}:${segment.action}:${startMillis(segment)}:${endMillis(segment)}"
    }

    private fun categoryName(segment: SponsorBlockSegment): String {
        val stringResource = when (segment.category) {
            SponsorBlockCategory.SPONSOR -> R.string.sponsor_block_category_sponsor_title
            SponsorBlockCategory.INTRO -> R.string.sponsor_block_category_intro_title
            SponsorBlockCategory.OUTRO -> R.string.sponsor_block_category_outro_title
            SponsorBlockCategory.INTERACTION -> R.string.sponsor_block_category_interaction_title
            SponsorBlockCategory.HIGHLIGHT -> R.string.sponsor_block_category_highlight_title
            SponsorBlockCategory.SELF_PROMO -> R.string.sponsor_block_category_self_promo_title
            SponsorBlockCategory.NON_MUSIC -> R.string.sponsor_block_category_non_music_title
            SponsorBlockCategory.PREVIEW -> R.string.sponsor_block_category_preview_title
            SponsorBlockCategory.FILLER -> R.string.sponsor_block_category_filler_title
            else -> R.string.sponsor_block_skipped_segment_fallback
        }
        return player.context.getString(stringResource)
    }

    private companion object {
        val SEGMENT_PROVIDER =
            object : SponsorBlockPlaybackDecision.SegmentProvider<SponsorBlockSegment> {
                override fun getStartMillis(segment: SponsorBlockSegment): Long {
                    return startMillis(segment)
                }

                override fun getEndMillis(segment: SponsorBlockSegment): Long {
                    return endMillis(segment)
                }

                override fun getCategory(segment: SponsorBlockSegment): SponsorBlockCategory? {
                    return segment.category
                }

                override fun getAction(segment: SponsorBlockSegment): SponsorBlockAction? {
                    return segment.action
                }
            }

        fun startMillis(segment: SponsorBlockSegment): Long = segment.startTime.roundToLong()

        fun endMillis(segment: SponsorBlockSegment): Long = segment.endTime.roundToLong()
    }
}
