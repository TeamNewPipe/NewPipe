package org.schabi.newpipe.player.helper;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.ServiceDescriptionElement;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;

import java.util.List;

/**
 * A {@link DashManifestParser} fixing YouTube DASH manifests to allow starting playback from the
 * newest period available instead of the earliest one in some cases.
 *
 * <p>
 * It changes the {@code availabilityStartTime} passed to a custom value doing the workaround.
 * A better approach to fix the issue should be investigated and used in the future.
 * </p>
 */
public class YoutubeDashLiveManifestParser extends DashManifestParser {

    // Result of Util.parseXsDateTime("1970-01-01T00:00:00Z")
    private static final long AVAILABILITY_START_TIME_TO_USE = 0;

    // There is no computation made with the availabilityStartTime value in the
    // parseMediaPresentationDescription method itself, so we can just override methods called in
    // this method using the workaround value
    // Overriding parsePeriod does not seem to be needed

    @SuppressWarnings("checkstyle:ParameterNumber")
    @NonNull
    @Override
    protected DashManifest buildMediaPresentationDescription(
            final long availabilityStartTime,
            final long durationMs,
            final long minBufferTimeMs,
            final boolean dynamic,
            final long minUpdateTimeMs,
            final long timeShiftBufferDepthMs,
            final long suggestedPresentationDelayMs,
            final long publishTimeMs,
            @Nullable final ProgramInformation programInformation,
            @Nullable final UtcTimingElement utcTiming,
            @Nullable final ServiceDescriptionElement serviceDescription,
            @Nullable final Uri location,
            @NonNull final List<Period> periods) {
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
                periods);
    }
}
