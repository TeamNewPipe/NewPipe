package org.schabi.newpipe.util;

import static org.schabi.newpipe.util.TouchUtils.getOffsetForHorizontalLine;

import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.external_communication.InternalUrlsHandler;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class CommentTextOnTouchListener implements View.OnTouchListener {
    public static final CommentTextOnTouchListener INSTANCE = new CommentTextOnTouchListener();

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (!(v instanceof TextView)) {
            return false;
        }
        final TextView widget = (TextView) v;
        final Object text = widget.getText();
        if (text instanceof Spanned) {
            final Spannable buffer = (Spannable) text;

            final int action = event.getAction();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                final int offset = getOffsetForHorizontalLine(widget, event);
                final ClickableSpan[] link = buffer.getSpans(offset, offset, ClickableSpan.class);

                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        if (link[0] instanceof URLSpan) {
                            final String url = ((URLSpan) link[0]).getURL();
                            if (!InternalUrlsHandler.handleUrlCommentsTimestamp(
                                    new CompositeDisposable(), v.getContext(), url)) {
                                ShareUtils.openUrlInBrowser(v.getContext(), url, false);
                            }
                        }
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                                buffer.getSpanEnd(link[0]));
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
