/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FocusAwareDrawerLayout.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

import org.schabi.newpipe.util.AndroidTvUtils;

/**
 * SeekBar, adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
  */
public final class FocusAwareSeekBar extends AppCompatSeekBar {
    private NestedListener listener;

    private ViewTreeObserver treeObserver;

    public FocusAwareSeekBar(final Context context) {
        super(context);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnSeekBarChangeListener(final OnSeekBarChangeListener l) {
        this.listener = l == null ? null : new NestedListener(l);

        super.setOnSeekBarChangeListener(listener);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (!isInTouchMode() && AndroidTvUtils.isConfirmKey(keyCode)) {
            releaseTrack();
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(final boolean gainFocus, final int direction,
                                  final Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (!isInTouchMode() && !gainFocus) {
            releaseTrack();
        }
    }

    private final ViewTreeObserver.OnTouchModeChangeListener touchModeListener = isInTouchMode -> {
        if (isInTouchMode) {
            releaseTrack();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        treeObserver = getViewTreeObserver();
        treeObserver.addOnTouchModeChangeListener(touchModeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (treeObserver == null || !treeObserver.isAlive()) {
            treeObserver = getViewTreeObserver();
        }

        treeObserver.removeOnTouchModeChangeListener(touchModeListener);
        treeObserver = null;

        super.onDetachedFromWindow();
    }

    private void releaseTrack() {
        if (listener != null && listener.isSeeking) {
            listener.onStopTrackingTouch(this);
        }
    }

    private final class NestedListener implements OnSeekBarChangeListener {
        private final OnSeekBarChangeListener delegate;

        boolean isSeeking;

        private NestedListener(final OnSeekBarChangeListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress,
                                      final boolean fromUser) {
            if (!seekBar.isInTouchMode() && !isSeeking && fromUser) {
                isSeeking = true;

                onStartTrackingTouch(seekBar);
            }

            delegate.onProgressChanged(seekBar, progress, fromUser);
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
            isSeeking = true;

            delegate.onStartTrackingTouch(seekBar);
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            isSeeking = false;

            delegate.onStopTrackingTouch(seekBar);
        }
    }
}
