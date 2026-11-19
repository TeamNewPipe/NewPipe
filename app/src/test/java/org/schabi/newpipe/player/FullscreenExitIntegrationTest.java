package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FullscreenExitIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void fullscreenSwipeUsesOrientationAwarePlayerAction() throws Exception {
        final String source = read(
                "org/schabi/newpipe/player/gesture/MainPlayerGestureListener.kt");
        final String onScrollEnd = methodBody(source, "override fun onScrollEnd");

        assertTrue(onScrollEnd.contains(
                "playerUi.toggleFullscreenWithOrientation()"));
        assertFalse(onScrollEnd.contains("playerUi.toggleFullscreen()"));
    }

    @Test
    public void manualFullscreenUsesExplicitRotationCallbackWithDirectFallback()
            throws Exception {
        final String source = read("org/schabi/newpipe/player/ui/MainPlayerUi.java");
        final String toggle = methodBody(
                source, "public void toggleFullscreenWithOrientation()");

        assertTrue(toggle.contains(
                "listener.onManualFullscreenButtonClicked(targetFullscreen)"));
        assertTrue(toggle.contains("setFullscreen(targetFullscreen);"));
    }

    @Test
    public void rotationCallbacksShareConfigurationOwnedFullscreenTransition()
            throws Exception {
        final String source = read(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java");
        final String automatic = methodBody(
                source, "public void onScreenRotationButtonClicked(final boolean fullscreen)");
        final String manual = methodBody(
                source, "public void onManualFullscreenButtonClicked(final boolean fullscreen)");
        final String rotation = methodBody(
                source, "private void requestFullscreenOrientation(");

        assertTrue(automatic.contains(
                "requestFullscreenOrientation(fullscreen)"));
        assertTrue(manual.contains(
                "requestFullscreenOrientation(fullscreen)"));
        assertTrue(rotation.contains("FullscreenOrientationPreference.targetOrientation("));
        assertTrue(rotation.contains("pendingFullscreenState"));
        assertTrue(rotation.contains("SCREEN_ORIENTATION_PORTRAIT"));
        assertTrue(rotation.contains(
                "activity.setRequestedOrientation(requestedOrientation)"));
        assertFalse(rotation.contains("DeviceUtils.isLandscape(requireContext())"));

        final String changed = methodBody(
                source, "public void onFullscreenStateChanged(final boolean fullscreen)");
        assertTrue(changed.contains(
                "!fullscreen && FullscreenOrientationPolicy.shouldRecreateDetailLayout("));
        assertTrue(changed.contains("recreateDetailLayoutForConfigurationChange();"));
    }

    @Test
    public void landscapeAutoFullscreenRequiresConfirmedContentNotTransientPlaybackState()
            throws Exception {
        final String playerUi = read("org/schabi/newpipe/player/ui/MainPlayerUi.java");
        final String checkLandscape = methodBody(
                playerUi, "public void checkLandscape()");
        assertTrue(checkLandscape.contains("videoContentOrientation"));
        assertTrue(checkLandscape.contains("setFullscreen(true);"));
        assertTrue(playerUi.contains(
                "FullscreenOrientationPolicy.supportsAutomaticFullscreen(contentOrientation)"));
        assertFalse(checkLandscape.contains("STATE_COMPLETED"));
        assertFalse(checkLandscape.contains("STATE_PAUSED"));
    }

    @Test
    public void explicitFullscreenSetterRemainsIdempotent() throws Exception {
        final String source = read("org/schabi/newpipe/player/ui/MainPlayerUi.java");
        final String setter = methodBody(source, "public void setFullscreen(");

        assertTrue(setter.contains("if (isFullscreen == fullscreen)"));
        assertTrue(setter.contains("isFullscreen = fullscreen;"));
        assertFalse(setter.contains("isFullscreen = !isFullscreen;"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
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
