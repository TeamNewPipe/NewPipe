package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN;

import android.content.res.Configuration;

import org.junit.Test;
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy;

import java.nio.file.Files;
import java.nio.file.Path;

public class VideoDetailOrientationHandlingTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main") : Path.of("app/src/main");

    @Test
    public void fullscreenTransitionCompletesOnlyAfterTargetStateIsApplied() throws Exception {
        assertFalse(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_LANDSCAPE, false, LANDSCAPE));
        assertTrue(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_LANDSCAPE, true, LANDSCAPE));
        assertFalse(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_PORTRAIT, true, LANDSCAPE));
        assertTrue(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_PORTRAIT, false, LANDSCAPE));

        final String fragment = readFragment();
        final String automaticAction = methodBody(
                fragment, "public void onScreenRotationButtonClicked(");
        final String manualAction = methodBody(
                fragment, "public void onManualFullscreenButtonClicked(");
        final String rotationAction = methodBody(
                fragment, "private void requestFullscreenOrientation(");

        assertTrue(automaticAction.contains(
                "requestFullscreenOrientation(fullscreen)"));
        assertTrue(manualAction.contains(
                "requestFullscreenOrientation(fullscreen)"));
        assertTrue(rotationAction.contains(
                "targetOrientation == Configuration.ORIENTATION_PORTRAIT"));
        assertTrue(rotationAction.contains(
                "setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)"));
        assertTrue(rotationAction.contains(
                "setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)"));
    }

    @Test
    public void portraitAndUnknownVideoDoNotWaitForOrientationDrivenState() {
        assertTrue(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_PORTRAIT, true, PORTRAIT));
        assertTrue(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_LANDSCAPE, false, PORTRAIT));
        assertTrue(FullscreenOrientationPolicy.isFullscreenStateApplied(
                Configuration.ORIENTATION_UNDEFINED, false, UNKNOWN));
    }

    @Test
    public void expandedPhoneVideoKeepsCurrentLayoutForLandscapeFullscreen() {
        assertTrue(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                true, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_PORTRAIT,
                true, true, false,
                true, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, true,
                true, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                false, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false,
                true, true));
    }

    @Test
    public void fullscreenLayoutGuardRunsBeforeWideLayoutRecreation()
            throws Exception {
        final String configuration = methodBody(
                readFragment(), "public void onConfigurationChanged(");
        final int keepLayout = configuration.indexOf(
                "FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(");
        final int recreate = configuration.indexOf(
                "FullscreenOrientationPolicy.shouldRecreateDetailLayout(");
        final int postedSync = configuration.indexOf("binding.getRoot().post(");
        assertTrue(keepLayout >= 0);
        assertTrue(recreate > keepLayout);
        assertTrue(postedSync > recreate);
        assertTrue(configuration.contains(
                "syncFullscreenWithOrientation(player.UIs().get(MainPlayerUi.class))"));
    }

    @Test
    public void expandedPlayerUsesConfigurationOrientation() throws Exception {
        final String fragment = readFragment();
        final String marker =
                "// Conditions when the player should be expanded to fullscreen";
        final int markerIndex = fragment.indexOf(marker);
        assertTrue(markerIndex >= 0);

        final int blockEnd = Math.min(fragment.length(), markerIndex + 700);
        final String expandedPlayerBlock = fragment.substring(markerIndex, blockEnd);
        assertTrue(expandedPlayerBlock.contains(
                "getResources().getConfiguration().orientation"));
        assertTrue(expandedPlayerBlock.contains(
                "Configuration.ORIENTATION_LANDSCAPE"));
        assertFalse(expandedPlayerBlock.contains(
                "DeviceUtils.isLandscape(requireContext())"));
    }

    @Test
    public void detailLayoutRestorationStillRebindsPlayer() throws Exception {
        final String restore = methodBody(
                readFragment(),
                "private void restoreDetailLayoutAfterConfigurationChange()");
        assertTrue(restore.contains("tryAddVideoPlayerView();"));
        assertTrue(restore.contains("updatePinnedPlayerLayout();"));
        assertTrue(restore.contains("syncFullscreenWithOrientation("));
    }

    private String readFragment() throws Exception {
        return Files.readString(projectDirectory.resolve(
                "java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
    }

    private static String methodBody(final String source, final String signature) {
        final int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method: " + signature, signatureIndex >= 0);
        final int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body: " + signature, bodyStart >= 0);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}
