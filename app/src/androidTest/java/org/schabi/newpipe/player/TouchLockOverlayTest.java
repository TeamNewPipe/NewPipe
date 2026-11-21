package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.about.AboutActivity;
import org.schabi.newpipe.views.TouchLockOverlay;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class TouchLockOverlayTest {
    @Test
    public void blocksRemoteNavigationAndPlaybackButAllowsUnlockAndVolume() {
        try (ActivityScenario<AboutActivity> scenario =
                     ActivityScenario.launch(AboutActivity.class)) {
            scenario.onActivity(activity -> {
                final TouchLockOverlay overlay = new TouchLockOverlay(activity, null);
                final Button unlock = new Button(activity);
                unlock.setText("Unlock");
                unlock.setFocusableInTouchMode(true);
                final AtomicInteger clicks = new AtomicInteger();
                unlock.setOnClickListener(view -> clicks.incrementAndGet());
                overlay.addView(unlock);
                activity.setContentView(overlay);
                final int exact = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
                overlay.measure(exact, exact);
                overlay.layout(0, 0, 400, 400);
                assertTrue(unlock.requestFocus());

                final int[] blockedKeys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_PROG_RED};
                for (final int code : blockedKeys) {
                    assertTrue(overlay.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code)));
                    assertTrue(overlay.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code)));
                    assertTrue(unlock.hasFocus());
                    assertEquals(0, clicks.get());
                }
                assertFalse(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)));
                assertFalse(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)));

                final int[] confirmKeys = {KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER};
                for (int i = 0; i < confirmKeys.length; i++) {
                    assertTrue(overlay.dispatchKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_DOWN, confirmKeys[i])));
                    assertTrue("Pressed for " + KeyEvent.keyCodeToString(confirmKeys[i]),
                            unlock.isPressed());
                    assertEquals(i, clicks.get());
                    assertTrue(overlay.dispatchKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_UP, confirmKeys[i])));
                    assertEquals(i + 1, clicks.get());
                }
                assertTrue(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
                assertTrue(overlay.dispatchKeyEvent(KeyEvent.changeFlags(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
                        KeyEvent.FLAG_CANCELED)));
                assertFalse(unlock.isPressed());
                assertEquals(confirmKeys.length, clicks.get());
            });
        }
    }

    @Test
    public void blocksPlaybackTouchesButKeepsUnlockReachable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final Context context = ApplicationProvider.getApplicationContext();
            final FrameLayout root = new FrameLayout(context);
            final AtomicInteger playbackTouches = new AtomicInteger();
            final View playback = new View(context);
            playback.setOnTouchListener((view, event) -> {
                playbackTouches.incrementAndGet();
                return true;
            });
            root.addView(playback, new FrameLayout.LayoutParams(400, 400));
            final TouchLockOverlay overlay = new TouchLockOverlay(context, null);
            final Button unlock = new Button(context);
            final AtomicInteger unlockTouches = new AtomicInteger();
            unlock.setText("Unlock");
            unlock.setOnTouchListener((view, event) -> {
                unlockTouches.incrementAndGet();
                return false;
            });
            unlock.setOnClickListener(view -> overlay.setVisibility(View.GONE));
            overlay.addView(unlock, new FrameLayout.LayoutParams(100, 100,
                    Gravity.RIGHT | Gravity.BOTTOM));
            root.addView(overlay, new FrameLayout.LayoutParams(400, 400));
            final int exact = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
            root.measure(exact, exact);
            root.layout(0, 0, 400, 400);

            tap(root, 50, 50);
            assertEquals(0, playbackTouches.get());
            tap(root, 350, 350);
            assertTrue(unlockTouches.get() > 0);
            // Unattached views queue performClick until attachment; execute that callback here.
            unlock.performClick();
            assertEquals(View.GONE, overlay.getVisibility());
            tap(root, 50, 50);
            assertTrue(playbackTouches.get() > 0);
        });
    }

    @Test
    public void hiddenUnlockRequiresARevealPressBeforeRemoteConfirmation() {
        try (ActivityScenario<AboutActivity> scenario =
                     ActivityScenario.launch(AboutActivity.class)) {
            scenario.onActivity(activity -> {
                final TouchLockOverlay overlay = new TouchLockOverlay(activity, null);
                final Button unlock = new Button(activity);
                unlock.setId(org.schabi.newpipe.R.id.touchUnlockButton);
                unlock.setFocusableInTouchMode(true);
                final AtomicInteger unlocks = new AtomicInteger();
                unlock.setOnClickListener(view -> unlocks.incrementAndGet());
                overlay.addView(unlock);
                activity.setContentView(overlay);
                final int exact = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
                overlay.measure(exact, exact);
                overlay.layout(0, 0, 400, 400);
                overlay.startLocked(true);
                unlock.setVisibility(View.INVISIBLE);

                assertTrue(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
                assertEquals(View.VISIBLE, unlock.getVisibility());
                assertTrue(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)));
                assertEquals(0, unlocks.get());

                assertTrue(unlock.requestFocus());
                assertTrue(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
                assertTrue(overlay.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)));
                assertEquals(1, unlocks.get());
                overlay.stopLocked();
            });
        }
    }

    @Test
    public void screenTapRevealsTheButtonWithoutUnlockingPlayback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final Context context = ApplicationProvider.getApplicationContext();
            final TouchLockOverlay overlay = new TouchLockOverlay(context, null);
            final Button unlock = new Button(context);
            unlock.setId(org.schabi.newpipe.R.id.touchUnlockButton);
            final AtomicInteger unlocks = new AtomicInteger();
            unlock.setOnClickListener(view -> unlocks.incrementAndGet());
            overlay.addView(unlock, new FrameLayout.LayoutParams(100, 100,
                    Gravity.RIGHT | Gravity.BOTTOM));
            final int size = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
            overlay.measure(size, size);
            overlay.layout(0, 0, 400, 400);
            overlay.startLocked(true);
            unlock.setVisibility(View.INVISIBLE);
            tap(overlay, 50, 50);
            assertEquals(View.VISIBLE, unlock.getVisibility());
            assertEquals(0, unlocks.get());
            overlay.stopLocked();
            assertEquals(View.VISIBLE, unlock.getVisibility());
        });
    }

    private static void tap(final View root, final float x, final float y) {
        final MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
        final MotionEvent up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, x, y, 0);
        try {
            root.dispatchTouchEvent(down);
            root.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }
}
