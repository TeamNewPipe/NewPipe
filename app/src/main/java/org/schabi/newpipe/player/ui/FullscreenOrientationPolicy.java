package org.schabi.newpipe.player.ui;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.schabi.newpipe.player.helper.PlayerRotationMode;

/**
 * Central policy for main-player fullscreen, orientation and detail-layout transitions.
 *
 * <p>The player fullscreen flag and Android configuration can briefly disagree while a requested
 * rotation, fragment rebind or screen-off resume is being completed. Callers should express the
 * desired fullscreen state explicitly instead of deriving enter/exit intent from the current
 * orientation.</p>
 */
public final class FullscreenOrientationPolicy {
    public static final int KEEP_FULLSCREEN_STATE = -1;
    public static final int EXIT_FULLSCREEN = 0;
    public static final int ENTER_FULLSCREEN = 1;
    public static final int EXPANDED_DETAIL_MIN_WIDTH_DP = 840;

    private static final float PORTRAIT_MAX_ASPECT_RATIO = 0.9f;
    private static final float LANDSCAPE_MIN_ASPECT_RATIO = 1.1f;

    public enum VideoContentOrientation {
        UNKNOWN,
        PORTRAIT,
        LANDSCAPE,
        SQUARE
    }

    private FullscreenOrientationPolicy() {
    }

    public static VideoContentOrientation classifyVideoContentOrientation(
            final int width,
            final int height,
            final float pixelWidthHeightRatio) {
        if (width <= 0 || height <= 0) {
            return VideoContentOrientation.UNKNOWN;
        }

        final float pixelRatio = pixelWidthHeightRatio > 0.0f
                ? pixelWidthHeightRatio : 1.0f;
        final float aspectRatio = width * pixelRatio / height;
        if (aspectRatio < PORTRAIT_MAX_ASPECT_RATIO) {
            return VideoContentOrientation.PORTRAIT;
        }
        if (aspectRatio > LANDSCAPE_MIN_ASPECT_RATIO) {
            return VideoContentOrientation.LANDSCAPE;
        }
        return VideoContentOrientation.SQUARE;
    }

    public static boolean supportsAutomaticFullscreen(
            final VideoContentOrientation contentOrientation) {
        return contentOrientation == VideoContentOrientation.LANDSCAPE;
    }

    public static boolean shouldUseOrientationAction(
            final VideoContentOrientation contentOrientation,
            final boolean landscape,
            final boolean targetFullscreen) {
        // A manual fullscreen exit in landscape must release the player orientation lock.
        // Its caller determines whether to keep Android's current physical orientation.
        if (landscape && !targetFullscreen) {
            return true;
        }
        if (contentOrientation == VideoContentOrientation.LANDSCAPE) {
            return true;
        }
        return contentOrientation == VideoContentOrientation.PORTRAIT
                && (targetFullscreen || landscape);
    }

    public static boolean shouldLockPortraitFullscreen(
            final boolean fullscreen,
            final VideoContentOrientation contentOrientation) {
        return fullscreen && contentOrientation == VideoContentOrientation.PORTRAIT;
    }

    public static boolean shouldAlignFullscreenToKnownContent(
            final boolean fullscreen,
            final VideoContentOrientation previousContentOrientation,
            final VideoContentOrientation contentOrientation) {
        return fullscreen
                && previousContentOrientation != contentOrientation
                && (contentOrientation == VideoContentOrientation.PORTRAIT
                || contentOrientation == VideoContentOrientation.LANDSCAPE
                || contentOrientation == VideoContentOrientation.SQUARE);
    }

    public static int targetConfigurationOrientation(
            final boolean fullscreen,
            final VideoContentOrientation contentOrientation) {
        if (!fullscreen) {
            return Configuration.ORIENTATION_PORTRAIT;
        }
        if (contentOrientation == VideoContentOrientation.PORTRAIT
                || contentOrientation == VideoContentOrientation.SQUARE) {
            return Configuration.ORIENTATION_PORTRAIT;
        }
        // Before Media3 reports a valid size, preserve the traditional fullscreen-button
        // behavior and request landscape. Once the content orientation becomes known,
        // shouldAlignFullscreenToKnownContent() corrects portrait and square streams.
        return Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isTargetOrientation(final int currentOrientation,
                                              final int targetOrientation) {
        return currentOrientation == targetOrientation;
    }

    public static int requestedLandscapeOrientation(final PlayerRotationMode rotationMode) {
        return rotationMode == PlayerRotationMode.FIXED
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    public static int resolveFullscreenState(
            final int orientation,
            final boolean fullscreen,
            final VideoContentOrientation contentOrientation,
            final boolean phoneVideoEligible,
            final int pendingOrientation,
            final int pendingFullscreenState) {
        if (pendingFullscreenState != KEEP_FULLSCREEN_STATE) {
            return orientation == pendingOrientation
                    ? pendingFullscreenState : KEEP_FULLSCREEN_STATE;
        }
        if (!phoneVideoEligible || !supportsAutomaticFullscreen(contentOrientation)) {
            return KEEP_FULLSCREEN_STATE;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return ENTER_FULLSCREEN;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT && fullscreen) {
            return EXIT_FULLSCREEN;
        }
        return KEEP_FULLSCREEN_STATE;
    }

    /**
     * A manual exit is allowed to keep the phone in landscape without reentering
     * fullscreen through the automatic orientation observer on the next layout pass.
     *
     * @param exitedInLandscape whether the user deliberately left fullscreen in landscape
     * @param orientation the current Android configuration orientation
     * @return true when automatic fullscreen entry should be suppressed temporarily
     */
    public static boolean shouldSuppressFullscreenForManualExit(
            final boolean exitedInLandscape, final int orientation) {
        return exitedInLandscape && orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isFullscreenStateApplied(
            final int orientation,
            final boolean fullscreen,
            final VideoContentOrientation contentOrientation) {
        if (!supportsAutomaticFullscreen(contentOrientation)) {
            return true;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return fullscreen;
        }
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return !fullscreen;
        }
        return true;
    }

    public static boolean shouldKeepPhonePlayerLayoutForLandscape(
            final int orientation,
            final boolean playerAvailable,
            final boolean videoPlayerSelected,
            final boolean audioOnly,
            final boolean playerExpanded,
            final boolean largeScreenDevice) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
                && playerAvailable
                && videoPlayerSelected
                && !audioOnly
                && playerExpanded
                && !largeScreenDevice;
    }

    public static boolean shouldUseWideLandscapeDetailLayout(final int orientation,
                                                             final int screenWidthDp) {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
                && screenWidthDp >= EXPANDED_DETAIL_MIN_WIDTH_DP;
    }

    public static boolean shouldRecreateDetailLayout(final boolean wideLandscapeLayout,
                                                     final int orientation,
                                                     final int screenWidthDp) {
        return wideLandscapeLayout
                != shouldUseWideLandscapeDetailLayout(orientation, screenWidthDp);
    }
    public static boolean shouldShowFullscreenButton(
            final boolean television,
            final boolean tablet,
            final boolean orientationLocked,
            final PlayerRotationMode rotationMode,
            final VideoContentOrientation contentOrientation) {
        // TVs never enter fullscreen through the phone's orientation-change path.
        return television || tablet || orientationLocked
                || rotationMode == PlayerRotationMode.FIXED
                || !supportsAutomaticFullscreen(contentOrientation);
    }

}
