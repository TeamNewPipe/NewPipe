package org.schabi.newpipe.views;

import static org.schabi.newpipe.util.TouchUtils.getOffsetForHorizontalLine;

import android.os.Handler;
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

    private static LongPressLinkMovementMethod sInstance;

    private Handler mLongClickHandler;
    private boolean mIsLongPressed = false;

    @Override
    public boolean onTouchEvent(final TextView widget,
                                final Spannable buffer,
                                @NonNull final MotionEvent event) {
        final int action = event.getAction();

        if (action == MotionEvent.ACTION_CANCEL && mLongClickHandler != null) {
            mLongClickHandler.removeCallbacksAndMessages(null);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            final int offset = getOffsetForHorizontalLine(widget, event);
            final LongPressClickableSpan[] link = buffer.getSpans(offset, offset,
                    LongPressClickableSpan.class);

            if (link.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if (mLongClickHandler != null) {
                        mLongClickHandler.removeCallbacksAndMessages(null);
                    }
                    if (!mIsLongPressed) {
                        link[0].onClick(widget);
                    }
                    mIsLongPressed = false;
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0]));
                    if (mLongClickHandler != null) {
                        mLongClickHandler.postDelayed(() -> {
                            link[0].onLongClick(widget);
                            mIsLongPressed = true;
                        }, LONG_PRESS_TIME);
                    }
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }


    public static MovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new LongPressLinkMovementMethod();
            sInstance.mLongClickHandler = new Handler();
        }

        return sInstance;
    }
}
