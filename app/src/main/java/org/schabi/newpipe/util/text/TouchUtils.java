package org.schabi.newpipe.util.text;

import android.text.Layout;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;

public final class TouchUtils {

    private TouchUtils() {
    }

    /**
     * Get the character offset on the closest line to the position pressed by the user of a
     * {@link TextView} from a {@link MotionEvent} which was fired on this {@link TextView}.
     *
     * @param textView the {@link TextView} on which the {@link MotionEvent} was fired
     * @param event    the {@link MotionEvent} which was fired
     * @return the character offset on the closest line to the position pressed by the user
     */
    public static int getOffsetForHorizontalLine(@NonNull final TextView textView,
                                                 @NonNull final MotionEvent event) {

        int x = (int) event.getX();
        int y = (int) event.getY();

        x -= textView.getTotalPaddingLeft();
        y -= textView.getTotalPaddingTop();

        x += textView.getScrollX();
        y += textView.getScrollY();

        final Layout layout = textView.getLayout();
        final int line = layout.getLineForVertical(y);
        return layout.getOffsetForHorizontal(line, x);
    }
}
