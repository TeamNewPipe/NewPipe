/*
 * Copyright 2019 Alexander Rvachev <rvacheva@nxt.ru>
 * FocusOverlayView.java is part of NewPipe
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

import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

public class LargeTextMovementMethod extends LinkMovementMethod {
    private final Rect visibleRect = new Rect();

    private int direction;

    @Override
    public void onTakeFocus(final TextView view, final Spannable text, final int dir) {
        Selection.removeSelection(text);

        super.onTakeFocus(view, text, dir);

        this.direction = dirToRelative(dir);
    }

    @Override
    protected boolean handleMovementKey(final TextView widget,
                                        final Spannable buffer,
                                        final int keyCode,
                                        final int movementMetaState,
                                        final KeyEvent event) {
        if (!doHandleMovement(widget, buffer, keyCode, movementMetaState, event)) {
            // clear selection to make sure, that it does not confuse focus handling code
            Selection.removeSelection(buffer);
            return false;
        }

        return true;
    }

    private boolean doHandleMovement(final TextView widget,
                                     final Spannable buffer,
                                     final int keyCode,
                                     final int movementMetaState,
                                     final KeyEvent event) {
        int newDir = keyToDir(keyCode);

        if (direction != 0 && newDir != direction) {
            return false;
        }

        this.direction = 0;

        ViewGroup root = findScrollableParent(widget);

        widget.getHitRect(visibleRect);

        root.offsetDescendantRectToMyCoords((View) widget.getParent(), visibleRect);

        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean up(final TextView widget, final Spannable buffer) {
        if (gotoPrev(widget, buffer)) {
            return true;
        }

        return super.up(widget, buffer);
    }

    @Override
    protected boolean left(final TextView widget, final Spannable buffer) {
        if (gotoPrev(widget, buffer)) {
            return true;
        }

        return super.left(widget, buffer);
    }

    @Override
    protected boolean right(final TextView widget, final Spannable buffer) {
        if (gotoNext(widget, buffer)) {
            return true;
        }

        return super.right(widget, buffer);
    }

    @Override
    protected boolean down(final TextView widget, final Spannable buffer) {
        if (gotoNext(widget, buffer)) {
            return true;
        }

        return super.down(widget, buffer);
    }

    private boolean gotoPrev(final TextView view, final Spannable buffer) {
        Layout layout = view.getLayout();
        if (layout == null) {
            return false;
        }

        View root = findScrollableParent(view);

        int rootHeight = root.getHeight();

        if (visibleRect.top >= 0) {
            // we fit entirely into the viewport, no need for fancy footwork
            return false;
        }

        int topExtra = -visibleRect.top;

        int firstVisibleLineNumber = layout.getLineForVertical(topExtra);

        // when deciding whether to pass "focus" to span, account for one more line
        // this ensures, that focus is never passed to spans partially outside scroll window
        int visibleStart = firstVisibleLineNumber == 0
                ? 0
                : layout.getLineStart(firstVisibleLineNumber - 1);

        ClickableSpan[] candidates = buffer.getSpans(
                visibleStart, buffer.length(), ClickableSpan.class);

        if (candidates.length != 0) {
            int a = Selection.getSelectionStart(buffer);
            int b = Selection.getSelectionEnd(buffer);

            int selStart = Math.min(a, b);
            int selEnd = Math.max(a, b);

            int bestStart = -1;
            int bestEnd = -1;

            for (int i = 0; i < candidates.length; i++) {
                int start = buffer.getSpanStart(candidates[i]);
                int end = buffer.getSpanEnd(candidates[i]);

                if ((end < selEnd || selStart == selEnd) && start >= visibleStart) {
                    if (end > bestEnd) {
                        bestStart = buffer.getSpanStart(candidates[i]);
                        bestEnd = end;
                    }
                }
            }

            if (bestStart >= 0) {
                Selection.setSelection(buffer, bestEnd, bestStart);
                return true;
            }
        }

        float fourLines = view.getTextSize() * 4;

        visibleRect.left = 0;
        visibleRect.right = view.getWidth();
        visibleRect.top = Math.max(0, (int) (topExtra - fourLines));
        visibleRect.bottom = visibleRect.top + rootHeight;

        return view.requestRectangleOnScreen(visibleRect);
    }

    private boolean gotoNext(final TextView view, final Spannable buffer) {
        Layout layout = view.getLayout();
        if (layout == null) {
            return false;
        }

        View root = findScrollableParent(view);

        int rootHeight = root.getHeight();

        if (visibleRect.bottom <= rootHeight) {
            // we fit entirely into the viewport, no need for fancy footwork
            return false;
        }

        int bottomExtra = visibleRect.bottom - rootHeight;

        int visibleBottomBorder = view.getHeight() - bottomExtra;

        int lineCount = layout.getLineCount();

        int lastVisibleLineNumber = layout.getLineForVertical(visibleBottomBorder);

        // when deciding whether to pass "focus" to span, account for one more line
        // this ensures, that focus is never passed to spans partially outside scroll window
        int visibleEnd = lastVisibleLineNumber == lineCount - 1
                ? buffer.length()
                : layout.getLineEnd(lastVisibleLineNumber - 1);

        ClickableSpan[] candidates = buffer.getSpans(0, visibleEnd, ClickableSpan.class);

        if (candidates.length != 0) {
            int a = Selection.getSelectionStart(buffer);
            int b = Selection.getSelectionEnd(buffer);

            int selStart = Math.min(a, b);
            int selEnd = Math.max(a, b);

            int bestStart = Integer.MAX_VALUE;
            int bestEnd = Integer.MAX_VALUE;

            for (int i = 0; i < candidates.length; i++) {
                int start = buffer.getSpanStart(candidates[i]);
                int end = buffer.getSpanEnd(candidates[i]);

                if ((start > selStart || selStart == selEnd) && end <= visibleEnd) {
                    if (start < bestStart) {
                        bestStart = start;
                        bestEnd = buffer.getSpanEnd(candidates[i]);
                    }
                }
            }

            if (bestEnd < Integer.MAX_VALUE) {
                // cool, we have managed to find next link without having to adjust self within view
                Selection.setSelection(buffer, bestStart, bestEnd);
                return true;
            }
        }

        // there are no links within visible area, but still some text past visible area
        // scroll visible area further in required direction
        float fourLines = view.getTextSize() * 4;

        visibleRect.left = 0;
        visibleRect.right = view.getWidth();
        visibleRect.bottom = Math.min((int) (visibleBottomBorder + fourLines), view.getHeight());
        visibleRect.top = visibleRect.bottom - rootHeight;

        return view.requestRectangleOnScreen(visibleRect);
    }

    private ViewGroup findScrollableParent(final View view) {
        View current = view;

        ViewParent parent;
        do {
            parent = current.getParent();

            if (parent == current || !(parent instanceof View)) {
                return (ViewGroup) view.getRootView();
            }

            current = (View) parent;

            if (current.isScrollContainer()) {
                return (ViewGroup) current;
            }
        }
        while (true);
    }

    private static int dirToRelative(final int dir) {
        switch (dir) {
            case View.FOCUS_DOWN:
            case View.FOCUS_RIGHT:
                return View.FOCUS_FORWARD;
            case View.FOCUS_UP:
            case View.FOCUS_LEFT:
                return View.FOCUS_BACKWARD;
        }

        return dir;
    }

    private int keyToDir(final int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_BACKWARD;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return View.FOCUS_FORWARD;
        }

        return View.FOCUS_FORWARD;
    }
}
