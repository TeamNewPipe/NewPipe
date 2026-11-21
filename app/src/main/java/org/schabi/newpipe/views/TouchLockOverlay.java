package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.schabi.newpipe.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Shields playback input while allowing the child unlock button to receive clicks. */
public final class TouchLockOverlay extends FrameLayout {
    private static final long AUTO_HIDE_DELAY_MS = 3_000L;
    @Nullable
    private View unlockButton;
    private boolean autoHideUnlock;
    private int revealingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable hideUnlock = () -> {
        final View button = unlockButton;
        if (button != null && autoHideUnlock && getVisibility() == View.VISIBLE) {
            button.setVisibility(View.INVISIBLE);
            setContentDescription(getResources().getString(R.string.player_touch_unlock_reveal));
        }
    };

    /**
     * Configure the lock overlay only while the player is touch-locked.
     * @param autoHide whether Unlock should disappear until the next user interaction
     */
    public void startLocked(final boolean autoHide) {
        autoHideUnlock = autoHide;
        unlockButton = findViewById(R.id.touchUnlockButton);
        revealingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        revealUnlock();
    }

    /** Restore the unlock button when leaving fullscreen or unlocking. */
    public void stopLocked() {
        removeCallbacks(hideUnlock);
        revealingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        autoHideUnlock = false;
        setContentDescription(null);
        if (unlockButton != null) {
            unlockButton.setVisibility(View.VISIBLE);
        }
    }

    private void revealUnlock() {
        removeCallbacks(hideUnlock);
        if (unlockButton == null) {
            return;
        }
        unlockButton.setVisibility(View.VISIBLE);
        setContentDescription(null);
        unlockButton.requestFocus();
        if (autoHideUnlock) {
            postDelayed(hideUnlock, AUTO_HIDE_DELAY_MS);
        }
    }

    public TouchLockOverlay(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_APP_SWITCH:
                return false;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (revealingKeyCode == event.getKeyCode()) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        revealingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                    }
                    return true;
                }
                if (autoHideUnlock && unlockButton != null
                        && unlockButton.getVisibility() != View.VISIBLE) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        revealingKeyCode = event.getKeyCode();
                        revealUnlock();
                    }
                    return true;
                }
                dispatchConfirmKeyEvent(event);
                return true;
            default:
                // A remote navigation press can also reveal Unlock, without triggering playback.
                if (autoHideUnlock && event.getAction() == KeyEvent.ACTION_DOWN) {
                    revealUnlock();
                }
                return true;
        }
    }

    private void dispatchConfirmKeyEvent(final KeyEvent event) {
        if (event.isCanceled()) {
            // Older Android buttons activate on canceled releases too.
            final View focused = findFocus();
            if (focused != null) {
                focused.cancelLongPress();
                focused.setPressed(false);
            }
            return;
        }
        // Android 6 only recognizes Center and Enter as button confirmation keys.
        final KeyEvent confirmEvent = event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER
                ? new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        KeyEvent.KEYCODE_ENTER, event.getRepeatCount(), event.getMetaState(),
                        event.getDeviceId(), event.getScanCode(), event.getFlags(),
                        event.getSource())
                : event;
        // After the first wake press, the unlock button may be visible yet still
        // lack window focus (for example while a dialog transitions). Dispatch the
        // next deliberate confirmation directly to the visible unlock control.
        if (unlockButton != null && unlockButton.getVisibility() == View.VISIBLE) {
            unlockButton.dispatchKeyEvent(confirmEvent);
        } else {
            super.dispatchKeyEvent(confirmEvent);
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && autoHideUnlock) {
            revealUnlock();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideUnlock);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent event) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        super.dispatchTouchEvent(event);
        return true;
    }
}
