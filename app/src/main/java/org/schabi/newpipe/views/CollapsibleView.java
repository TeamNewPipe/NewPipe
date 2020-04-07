/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * CollapsibleView.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.schabi.newpipe.util.AnimationUtils;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;

import icepick.Icepick;
import icepick.State;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static org.schabi.newpipe.MainActivity.DEBUG;

/**
 * A view that can be fully collapsed and expanded.
 */
public class CollapsibleView extends LinearLayout {
    private static final String TAG = CollapsibleView.class.getSimpleName();

    private static final int ANIMATION_DURATION = 420;

    public static final int COLLAPSED = 0;
    public static final int EXPANDED = 1;

    @State
    @ViewMode
    int currentState = COLLAPSED;
    private boolean readyToChangeState;

    private int targetHeight = -1;
    private ValueAnimator currentAnimator;
    private final List<StateListener> listeners = new ArrayList<>();

    public CollapsibleView(final Context context) {
        super(context);
    }

    public CollapsibleView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public CollapsibleView(final Context context, @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CollapsibleView(final Context context, final AttributeSet attrs, final int defStyleAttr,
                           final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Collapse/expand logic
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * This method recalculates the height of this view so it <b>must</b> be called when
     * some child changes (e.g. add new views, change text).
     */
    public void ready() {
        if (DEBUG) {
            Log.d(TAG, getDebugLogString("ready() called"));
        }

        measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.UNSPECIFIED);
        targetHeight = getMeasuredHeight();

        getLayoutParams().height = currentState == COLLAPSED ? 0 : targetHeight;
        requestLayout();
        broadcastState();

        readyToChangeState = true;

        if (DEBUG) {
            Log.d(TAG, getDebugLogString("ready() *after* measuring"));
        }
    }

    public void collapse() {
        if (DEBUG) {
            Log.d(TAG, getDebugLogString("collapse() called"));
        }

        if (!readyToChangeState) {
            return;
        }

        final int height = getHeight();
        if (height == 0) {
            setCurrentState(COLLAPSED);
            return;
        }

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        currentAnimator = AnimationUtils.animateHeight(this, ANIMATION_DURATION, 0);

        setCurrentState(COLLAPSED);
    }

    public void expand() {
        if (DEBUG) {
            Log.d(TAG, getDebugLogString("expand() called"));
        }

        if (!readyToChangeState) {
            return;
        }

        final int height = getHeight();
        if (height == this.targetHeight) {
            setCurrentState(EXPANDED);
            return;
        }

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        currentAnimator = AnimationUtils.animateHeight(this, ANIMATION_DURATION, this.targetHeight);
        setCurrentState(EXPANDED);
    }

    public void switchState() {
        if (!readyToChangeState) {
            return;
        }

        if (currentState == COLLAPSED) {
            expand();
        } else {
            collapse();
        }
    }

    @ViewMode
    public int getCurrentState() {
        return currentState;
    }

    public void setCurrentState(@ViewMode final int currentState) {
        this.currentState = currentState;
        broadcastState();
    }

    public void broadcastState() {
        for (StateListener listener : listeners) {
            listener.onStateChanged(currentState);
        }
    }

    /**
     * Add a listener which will be listening for changes in this view (i.e. collapsed or expanded).
     * @param listener {@link StateListener} to be added
     */
    public void addListener(final StateListener listener) {
        if (listeners.contains(listener)) {
            throw new IllegalStateException("Trying to add the same listener multiple times");
        }

        listeners.add(listener);
    }

    /**
     * Remove a listener so it doesn't receive more state changes.
     * @param listener {@link StateListener} to be removed
     */
    public void removeListener(final StateListener listener) {
        listeners.remove(listener);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    @Override
    public Parcelable onSaveInstanceState() {
        return Icepick.saveInstanceState(this, super.onSaveInstanceState());
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        super.onRestoreInstanceState(Icepick.restoreInstanceState(this, state));

        ready();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal
    //////////////////////////////////////////////////////////////////////////*/

    public String getDebugLogString(final String description) {
        return String.format("%-100s → %s",
                description, "readyToChangeState = [" + readyToChangeState + "], "
                        + "currentState = [" + currentState + "], "
                        + "targetHeight = [" + targetHeight + "], "
                        + "mW x mH = [" + getMeasuredWidth() + "x" + getMeasuredHeight() + "], "
                        + "W x H = [" + getWidth() + "x" + getHeight() + "]");
    }

    @Retention(SOURCE)
    @IntDef({COLLAPSED, EXPANDED})
    public @interface ViewMode { }

    /**
     * Simple interface used for listening state changes of the {@link CollapsibleView}.
     */
    public interface StateListener {
        /**
         * Called when the state changes.
         *
         * @param newState the state that the {@link CollapsibleView} transitioned to,<br/>
         *                 it's an integer being either {@link #COLLAPSED} or {@link #EXPANDED}
         */
        void onStateChanged(@ViewMode int newState);
    }
}
