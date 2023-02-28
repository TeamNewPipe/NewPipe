package org.schabi.newpipe.util.text;

import static org.schabi.newpipe.util.text.TouchUtils.getOffsetForHorizontalLine;

import android.annotation.SuppressLint;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public class CommentTextOnTouchListener implements View.OnTouchListener {
    public static final CommentTextOnTouchListener INSTANCE = new CommentTextOnTouchListener();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (!(v instanceof TextView)) {
            return false;
        }
        final TextView widget = (TextView) v;
        final CharSequence text = widget.getText();
        if (text instanceof Spanned) {
            final Spanned buffer = (Spanned) text;
            final int action = event.getAction();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                final int offset = getOffsetForHorizontalLine(widget, event);
                final ClickableSpan[] links = buffer.getSpans(offset, offset, ClickableSpan.class);

                if (links.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        links[0].onClick(widget);
                    }
                    // we handle events that intersect links, so return true
                    return true;
                }
            }
        }
        return false;
    }
}
