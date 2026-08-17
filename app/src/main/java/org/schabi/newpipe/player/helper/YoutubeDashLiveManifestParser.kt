@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.helper

import android.net.Uri
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.ProgramInformation
import androidx.media3.exoplayer.dash.manifest.ServiceDescriptionElement
import androidx.media3.exoplayer.dash.manifest.UtcTimingElement

/**
 * A [DashManifestParser] fixing YouTube DASH manifests to allow starting playback from the
 * newest period available instead of the earliest one in some cases.
 *
 * <p>
 * It changes the {@code availabilityStartTime} passed to a custom value doing the workaround.
 * A better approach to fix the issue should be investigated and used in the future.
 * </p>
 */
open class YoutubeDashLiveManifestParser : DashManifestParser() {

    // Result of Util.parseXsDateTime("1970-01-01T00:00:00Z")
    // There is no computation made with the availabilityStartTime value in the
    // parseMediaPresentationDescription method itself, so we can just override methods called in
    // this method using the workaround value
    // Overriding parsePeriod does not seem to be needed

    override fun buildMediaPresentationDescription(
        availabilityStartTime: Long,
        durationMs: Long,
        minBufferTimeMs: Long,
        dynamic: Boolean,
        minUpdateTimeMs: Long,
        timeShiftBufferDepthMs: Long,
        suggestedPresentationDelayMs: Long,
        publishTimeMs: Long,
        programInformation: ProgramInformation?,
        utcTiming: UtcTimingElement?,
        serviceDescription: ServiceDescriptionElement?,
        location: Uri?,
        periods: List<Period>
    ): DashManifest {
        return super.buildMediaPresentationDescription(
            AVAILABILITY_START_TIME_TO_USE,
            durationMs,
            minBufferTimeMs,
            dynamic,
            minUpdateTimeMs,
            timeShiftBufferDepthMs,
            suggestedPresentationDelayMs,
            publishTimeMs,
            programInformation,
            utcTiming,
            serviceDescription,
            location,
            periods
        )
    }

    companion object {
        private const val AVAILABILITY_START_TIME_TO_USE: Long = 0
    }
}
