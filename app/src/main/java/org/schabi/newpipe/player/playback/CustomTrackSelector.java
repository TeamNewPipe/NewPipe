package org.schabi.newpipe.player.playback;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.Assertions;

/**
 * This class allows irregular text language labels for use when selecting text captions and
 * is mostly a copy-paste from {@link DefaultTrackSelector}.
 *
 * This is a hack and should be removed once ExoPlayer fixes language normalization to accept
 * a broader set of languages. 
 * */
public class CustomTrackSelector extends DefaultTrackSelector {
    private static final int WITHIN_RENDERER_CAPABILITIES_BONUS = 1000;

    private String preferredTextLanguage;

    public CustomTrackSelector(TrackSelection.Factory adaptiveTrackSelectionFactory) {
        super(adaptiveTrackSelectionFactory);
    }

    public String getPreferredTextLanguage() {
        return preferredTextLanguage;
    }

    public void setPreferredTextLanguage(@NonNull final String label) {
        Assertions.checkNotNull(label);
        if (!label.equals(preferredTextLanguage)) {
            preferredTextLanguage = label;
            invalidate();
        }
    }

    /** @see DefaultTrackSelector#formatHasLanguage(Format, String)*/
    protected static boolean formatHasLanguage(Format format, String language) {
        return language != null && TextUtils.equals(language, format.language);
    }

    /** @see DefaultTrackSelector#formatHasNoLanguage(Format)*/
    protected static boolean formatHasNoLanguage(Format format) {
        return TextUtils.isEmpty(format.language) || formatHasLanguage(format, C.LANGUAGE_UNDETERMINED);
    }

    /** @see DefaultTrackSelector#selectTextTrack(TrackGroupArray, int[][], Parameters) */
    @Override
    protected TrackSelection selectTextTrack(TrackGroupArray groups, int[][] formatSupport,
                                             Parameters params) {
        TrackGroup selectedGroup = null;
        int selectedTrackIndex = 0;
        int selectedTrackScore = 0;
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            TrackGroup trackGroup = groups.get(groupIndex);
            int[] trackFormatSupport = formatSupport[groupIndex];
            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                if (isSupported(trackFormatSupport[trackIndex],
                        params.exceedRendererCapabilitiesIfNecessary)) {
                    Format format = trackGroup.getFormat(trackIndex);
                    int maskedSelectionFlags =
                            format.selectionFlags & ~params.disabledTextTrackSelectionFlags;
                    boolean isDefault = (maskedSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0;
                    boolean isForced = (maskedSelectionFlags & C.SELECTION_FLAG_FORCED) != 0;
                    int trackScore;
                    boolean preferredLanguageFound = formatHasLanguage(format, preferredTextLanguage);
                    if (preferredLanguageFound
                            || (params.selectUndeterminedTextLanguage && formatHasNoLanguage(format))) {
                        if (isDefault) {
                            trackScore = 8;
                        } else if (!isForced) {
                            // Prefer non-forced to forced if a preferred text language has been specified. Where
                            // both are provided the non-forced track will usually contain the forced subtitles as
                            // a subset.
                            trackScore = 6;
                        } else {
                            trackScore = 4;
                        }
                        trackScore += preferredLanguageFound ? 1 : 0;
                    } else if (isDefault) {
                        trackScore = 3;
                    } else if (isForced) {
                        if (formatHasLanguage(format, params.preferredAudioLanguage)) {
                            trackScore = 2;
                        } else {
                            trackScore = 1;
                        }
                    } else {
                        // Track should not be selected.
                        continue;
                    }
                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        trackScore += WITHIN_RENDERER_CAPABILITIES_BONUS;
                    }
                    if (trackScore > selectedTrackScore) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }
        return selectedGroup == null ? null
                : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
    }
}
