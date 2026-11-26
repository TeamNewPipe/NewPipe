/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.mediasource

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import java.io.IOException
import org.schabi.newpipe.player.Player

/**
 * Use YouTube's refreshable DASH DVR window when available. A finite/static or broken manifest
 * falls back to HLS before publishing a timeline, so playback never starts on a frozen DVR snapshot.
 * Full live callers can disallow that fallback so a refresh cannot silently discard the DVR
 * window. Once playback starts, manifest updates keep the source and the user's seek position.
 */
class YoutubeLiveMediaSource @JvmOverloads constructor(
    private val dashSource: MediaSource,
    private val hlsSource: MediaSource,
    private val allowHlsFallback: Boolean = true
) : CompositeMediaSource<Int>() {
    private var preparationError: IOException? = null
    private var usingHls = false
    private var publishedTimeline = false
    private var timelineRefreshCount = 0
    private val selectedSource: MediaSource
        get() = if (usingHls) hlsSource else dashSource

    override fun getMediaItem(): MediaItem = selectedSource.mediaItem

    internal fun dashForRefresh(): DashMediaSource? = if (!usingHls && publishedTimeline) dashSource as? DashMediaSource else null

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        preparationError = null
        usingHls = false
        publishedTimeline = false
        timelineRefreshCount = 0
        prepareChildSource(DASH, dashSource)
    }

    override fun onChildSourceInfoRefreshed(id: Int, mediaSource: MediaSource, timeline: Timeline) {
        if (usingHls && id == DASH) return
        if (Player.DEBUG) {
            timelineRefreshCount++
            if (timeline.isEmpty) {
                Log.d(TAG, "Live timeline update $timelineRefreshCount: source=$id empty")
            } else {
                val window = timeline.getWindow(0, Timeline.Window())
                val dash = window.manifest as? DashManifest
                Log.d(
                    TAG,
                    "Live timeline update $timelineRefreshCount: source=$id " +
                        "dynamic=${window.isDynamic} durationMs=${window.durationMs} " +
                        "defaultPositionMs=${window.defaultPositionMs} " +
                        "dashDynamic=${dash?.dynamic} dashUpdateMs=${dash?.minUpdatePeriodMs} " +
                        "dashPeriods=${dash?.periodCount}"
                )
            }
        }
        if (!usingHls && !publishedTimeline) {
            if (timeline.isEmpty) return
            val manifest = timeline.getWindow(0, Timeline.Window()).manifest as? DashManifest
            if (manifest == null || !isRefreshable(manifest)) {
                if (Player.DEBUG) {
                    Log.d(TAG, "DASH timeline is not refreshable; allowHlsFallback=$allowHlsFallback")
                }
                if (allowHlsFallback) {
                    useHls()
                } else {
                    preparationError = FullLiveDvrUnavailableException("The Full live DASH manifest has no refreshable DVR window")
                }
                return
            }
        }
        publishedTimeline = true
        refreshSourceInfo(timeline)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        preparationError?.let { throw it }
        try {
            super.maybeThrowSourceInfoRefreshError()
        } catch (error: IOException) {
            if (!allowHlsFallback && !publishedTimeline) {
                throw FullLiveDvrUnavailableException("Full live DASH preparation failed", error)
            }
            if (usingHls || publishedTimeline) throw error
            if (Player.DEBUG) {
                Log.d(TAG, "DASH preparation failed; using HLS: ${error.javaClass.simpleName}")
            }
            useHls()
        }
    }

    private fun useHls() {
        usingHls = true
        releaseChildSource(DASH)
        prepareChildSource(HLS, hlsSource)
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod = selectedSource.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        selectedSource.releasePeriod(mediaPeriod)
    }

    companion object {
        private const val TAG = "YoutubeLiveMediaSource"
        private const val DASH = 0
        private const val HLS = 1

        internal fun isRefreshable(manifest: DashManifest): Boolean {
            if (!manifest.dynamic || manifest.durationMs != C.TIME_UNSET) return false
            if (manifest.minUpdatePeriodMs != C.TIME_UNSET) return true
            if (manifest.availabilityStartTimeMs == C.TIME_UNSET || manifest.periodCount == 0) return false

            // Media3 also advances a live window from clock-based, unbounded segment indexes
            // without reloading the MPD. A finite SegmentTimeline or bounded template still
            // needs explicit manifest refreshes; accepting it would freeze the DVR snapshot.
            val lastPeriod = manifest.periodCount - 1
            val periodDurationUs = manifest.getPeriodDurationUs(lastPeriod)
            val adaptationSets = manifest.getPeriod(lastPeriod).adaptationSets
            // Media3's implicit-refresh check includes text adaptation sets. Check every
            // representation too, so an adaptive switch cannot select a finite alternative.
            return adaptationSets.isNotEmpty() && adaptationSets.all { adaptation ->
                adaptation.representations.isNotEmpty() && adaptation.representations.all { representation ->
                    val index = representation.index
                    index != null && !index.isExplicit &&
                        index.getSegmentCount(periodDurationUs) == DashSegmentIndex.INDEX_UNBOUNDED.toLong()
                }
            }
        }
    }
}

internal class FullLiveDvrUnavailableException(message: String, cause: IOException? = null) : IOException(message, cause)
