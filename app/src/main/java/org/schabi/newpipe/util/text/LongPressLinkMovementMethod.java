package org.schabi.newpipe.util.text;

import static org.schabi.newpipe.util.text.TouchUtils.getOffsetForHorizontalLine;

import android.os.Handler;
import android.os.Looper;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.NonNull;

// Class adapted from https://stackoverflow.com/a/31786969

public class LongPressLinkMovementMethod extends LinkMovementMethod {

    private static final int LONG_PRESS_TIME = ViewConfiguration.getLongPressTimeout();

    private static LongPressLinkMovementMethod instance;

    private Handler longClickHandler;
    private boolean isLongPressed = false;

    @Override
    public boolean onTouchEvent(@NonNull final TextView widget,
                                @NonNull final Spannable buffer,
                                @NonNull final MotionEvent event) {
        final int action = event.getAction();

        if (action == MotionEvent.ACTION_CANCEL && longClickHandler != null) {
            longClickHandler.removeCallbacksAndMessages(null);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            final int offset = getOffsetForHorizontalLine(widget, event);
            final LongPressClickableSpan[] link = buffer.getSpans(offset, offset,
                    LongPressClickableSpan.class);

            if (link.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if (longClickHandler != null) {
                        longClickHandler.removeCallbacksAndMessages(null);
                    }
                    if (!isLongPressed) {
                        link[0].onClick(widget);
                    }
                    isLongPressed = false;
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]));
                    if (longClickHandler != null) {
                        longClickHandler.postDelayed(() -> {
                            link[0].onLongClick(widget);
                            isLongPressed = true;
                        }, LONG_PRESS_TIME);
                    }
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    public static MovementMethod getInstance() {
        if (instance == null) {
            instance = new LongPressLinkMovementMethod();
            instance.longClickHandler = new Handler(Looper.myLooper());
        }

        return instance;
    }
}
