package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.text.BreakIterator;
import java.lang.ref.WeakReference;

import org.schabi.newpipe.util.AnimationUtil;
import org.schabi.newpipe.util.NewPipeTextViewHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

/**
 * An {@link AppCompatTextView} which uses {@link ShareUtils#shareText(Context, String, String)}
 * when sharing selected text by using the {@code Share} command of the floating actions.
 * <p>
 * This allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing text
 * from {@link AppCompatTextView} on EMUI devices.
 * </p>
 */
public class NewPipeTextView extends AppCompatTextView implements AnimationUtil.OnAnimateListener {

    public NewPipeTextView(@NonNull final Context context) {
        super(context);
    }

    public NewPipeTextView(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public NewPipeTextView(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTextContextMenuItem(final int id) {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this);
            return true;
        }
        return super.onTextContextMenuItem(id);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Methods and interfaces
    //////////////////////////////////////////////////////////////////////////*/

    private static final boolean DEBUG = android.os.Debug.isDebuggerConnected();

    public int getDisplayLines() {
        return getMaxLines() == -1 ? getLineCount() : Math.min(getMaxLines(), getLineCount());
    }

    /* the transient ellipsizing state: returns -1 if undetermined (usually before
     * the layout is first drawn), 1 if we determine the text is shown in part and thus expandable,
     * 2 if TextView reports the text is ellipsized (by it), 0 otherwise if text is in full view */
    public int ellipsisState() {
        if (getLayout() == null) {
            return -1;
        } else {
            return getLayout().getLineEnd(getDisplayLines() - 1) != getText().length() ? 1
                    : (getLayout().getEllipsisCount(getDisplayLines() - 1) > 0 ? 2 : 0);
        }
    }

    public interface OnToggleListener {
        void onToggle(NewPipeTextView textView, boolean expanded);
    }

    private OnToggleListener onToggleListener;

    public void setOnToggleListener(final OnToggleListener listener) {
        onToggleListener = listener;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Animations stuff
    //////////////////////////////////////////////////////////////////////////*/

    public void toggle(final int collapsedLines, final int expandedLines, final int duration) {
        toggle(collapsedLines, expandedLines, duration, duration);
    }

    public void toggle(final int collapsedLines, final int expandedLines,
                       final int collapseDuration, final int expandDuration) {
        if ((ellipsisState() == 1 && getMaxLines() < expandedLines)
                || getMaxLines() > collapsedLines) {

            final int initialHeight = getHeight();
            final boolean isCollapsing = getMaxLines() > collapsedLines;
            AnimationUtil.toggle(this,
                    isCollapsing ? collapseDuration : expandDuration,
                    isCollapsing ? collapsedLines : expandedLines,
                    isCollapsing, this);
        }
    }
    /* implements AnimationUtil.OnAnimateListener */
    @Override
    public void onAnimate(final int animated, final int initial, final int target) {
        // determine position of the sliding window for the ellipsis during animation
        crossfadeEllipsis = target > initial
                ? 1 - (float) (animated - initial) / (float) (target - initial)
                : (float) (initial - animated) / (float) (initial - target);
    }
    @Override
    public void onAnimationEnd(final View v, final boolean reversed, final boolean expanded) {
        crossfadeEllipsis = -1;
        if (!reversed && onToggleListener != null) {
            onToggleListener.onToggle(this, expanded);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Static heloer functions
    //////////////////////////////////////////////////////////////////////////*/

    /* Bounds related static helper functions for EllipsizeParams */
    private static void setStartEdge(final RectF bounds, final float pos, final boolean isLTR) {
        setBoundsEdge(bounds, pos, isLTR, true, 0);
    }
    private static void setEndEdge(final RectF bounds, final float pos, final boolean isLTR) {
        setBoundsEdge(bounds, pos, isLTR, false, 0);
    }
    private static void offsetStartEdge(final RectF bounds, final boolean isLTR,
                                        final float offset) {
        setBoundsEdge(bounds, getStartEdge(bounds, isLTR), isLTR, true, offset);
    }
    private static void offsetEndEdge(final RectF bounds, final boolean isLTR, final float offset) {
        setBoundsEdge(bounds, getEndEdge(bounds, isLTR), isLTR, false, offset);
    }
    private static void setBoundsEdge(final RectF bounds, final float pos, final boolean isLTR,
                                      final boolean startEdge, final float offset) {
        if ((isLTR && startEdge) || (!isLTR && !startEdge)) {
            bounds.left = isLTR ? pos + offset : pos - offset;
        } else {
            bounds.right = isLTR ? pos + offset : pos - offset;
        }
    }
    private static float getStartEdge(final RectF bounds, final boolean isLTR) {
        return isLTR ? bounds.left : bounds.right;
    }
    private static float getEndEdge(final RectF bounds, final boolean isLTR) {
        return isLTR ? bounds.right : bounds.left;
    }
    private static boolean sameStartEdge(final RectF bounds, final Rect lineBounds,
                                         final boolean isLTR) {
        return isLTR ? (bounds.left == lineBounds.left) : (bounds.right == lineBounds.right);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Ellipsizing companions
    //////////////////////////////////////////////////////////////////////////*/

    /* our home-grown cache holding the metrics for drawing the ellipsis and/or a ellipsis mask */
    private static class EllipsizeParams {
        public WeakReference<Layout> layoutReference;
        public String ellipsisString;
        public int ellipsisLine;
        public RectF ellipsisBounds;
        public int ellipsisBaseline;
        public boolean ellipsisLTR;

        /* a cache which holds metrics for a single line; namely the last line temporarily inside
         * EllipsizeParams.initialize() because the calls to Layout appear to be expensive */
        protected static class LineMetrics {
            /* properties */
            public int line; // zero based line number
            public boolean isLTR;
            /* bounds */
            public int width;
            public int top;
            public int bottom;
            /* canvas coordinates */
            public int baseline;
            public float lineMax;
            /* text offsets */
            public int lineStart;
            public int lineEnd;
        }

        private LineMetrics warmUpLineMetrics(final int line, final Layout layout) {
            final LineMetrics metrics = new LineMetrics();
            metrics.line = line;
            final Rect bounds = new Rect();
            // from AOSP: bounds = 0, getLineTop(line), mWidth, getLineTop(line + 1)
            metrics.baseline = layout.getLineBounds(line, bounds);
            metrics.width = bounds.right;
            metrics.top = bounds.top;
            metrics.bottom = bounds.bottom;

            metrics.isLTR = (layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT);
            metrics.lineMax = layout.getLineMax(line);
            metrics.lineStart = layout.getLineStart(line);
            metrics.lineEnd = layout.getLineEnd(line);
            return metrics;
        }

        public Layout getLayout() {
            return layoutReference != null && layoutReference.get() != null
                    ? layoutReference.get() : null;
        }

        /* Equivalent helper functions to avoid hitting Layout.getLineExtend() which seems expensive
         * These short-circuits blissfully ignore getParagraphAlignment() and assume ALIGN_NORMAL */
        protected float getLineLeft(final LineMetrics metrics) {
            // from AOSP: mWidth - getLineMax(line)if ALIGN_RIGHT or 0
            return metrics.isLTR ? 0 : metrics.width - metrics.lineMax;
        }
        protected float getLineRight(final LineMetrics metrics) {
            // from AOSP: mWidth if ALIGN_RIGHT or getLineMax(line)
            return metrics.isLTR ? metrics.lineMax : metrics.width;
        }

        /* helper functions which fetch from the warmed up cache */
        private int getLineBaseLine(final LineMetrics metrics) {
            return metrics.baseline;
        }
        private int getLineWidth(final LineMetrics metrics) {
            return metrics.width;
        }
        private int getLineStart(final LineMetrics metrics) {
            return metrics.lineStart;
        }
        private int getLineEnd(final LineMetrics metrics) {
            return metrics.lineEnd;
        }
        private float getLineRemainingWidth(final LineMetrics metrics) {
            return metrics.isLTR ? metrics.width - getLineRight(metrics) : getLineLeft(metrics);
        }

        /* debug helpers */
        protected RectF getLineRemainderBounds(final LineMetrics metrics) {
            if (metrics.isLTR) {
                return new RectF(getLineRight(metrics), metrics.top, metrics.width, metrics.bottom);
            } else {
                return new RectF(0, metrics.top, getLineLeft(metrics), metrics.bottom);
            }
        }
        protected Rect getLineBounds(final LineMetrics metrics) {
            return new Rect(0, metrics.top, metrics.width, metrics.bottom);
        }
        private float getLineEndEdge(final LineMetrics metrics, final float offset) {
            return metrics.isLTR ? getLineRight(metrics) + offset : getLineLeft(metrics) - offset;
        }

        EllipsizeParams() {
            // just an empty default no-arg ctor for subclass to initialize
        }

        EllipsizeParams(final NewPipeTextView textView) {
            initialize(textView);
        }

        public static EllipsizeParams newInstance(final NewPipeTextView textView) {
            final Layout layout = textView.getLayout();
            if (layout == null) {
                return null;
            }
            // short-circuits are unimplemented for Alignment other than ALIGN_NORMAL
            // for the simple reason that there is currently no use case
            if (layout.getParagraphAlignment(textView.getDisplayLines() - 1)
                    != Layout.Alignment.ALIGN_NORMAL) {
                // fall back gracefully to a relay subclass just in case
                return new EllipsizeParamsLayout(textView);
            }
            if (DEBUG) {
                return new EllipsizeParamsDebug(textView);
            } else {
                return new EllipsizeParams(textView);
            }
        }

        /* Our custom ellipsizing strategy implemented as follows: where lines of text don't fit,
         * - append the ellipsis to the end of the last line displayed, if screen estate fits; else
         * - crunch the last bit (word) of the last line to make room for the ellipsis.
         * It's been longstanding (bug) in Android's TextView the internal ellipsizing doesn't work
         * with BufferType.SPANNABLE (necessitated by setMovementMethod() other than null) at all
         * (so we're bound to roll our own). A feature over TextView's internal implementation
         * is that we crunch by word rather than characters (so the user won't be caught by surprise
         * when 'app...' was meant to mean 'approach' upon expanding the text, just as an example).
         * (We defer to BreakIterator for the last 'word' since not every language delimits words
         * by a space.) */
        protected LineMetrics initialize(final NewPipeTextView textView) {
            layoutReference = new WeakReference<>(textView.getLayout());
            final Layout layout = textView.getLayout();
            final LineMetrics lastLine = warmUpLineMetrics(textView.getDisplayLines() - 1, layout);
            final Rect lastLineBounds = getLineBounds(lastLine);

            if (lastLine.isLTR) {
                ellipsisString = "\u00a0\u2026"; // non-breaking space, ellipsis
            } else {
                ellipsisString = "\u2026\u00a0"; // reversed
            }
            // determine screen estate the ellipsis takes
            final float ellipsisWidth = layout.getPaint().measureText(ellipsisString);
            final float shortfall = ellipsisWidth - getLineRemainingWidth(lastLine);
            int lastChar = getLineEnd(lastLine);
            // this would be the reference point to determine if the clipping mask
            // would eventually kick in at the end of the evaluations
            final int origLastChar = lastChar;
            if (shortfall > 0) {
                // find the closest text offset where the ellipsis fits in
                lastChar = layout.getOffsetForHorizontal(lastLine.line,
                        getLineEndEdge(lastLine, -shortfall));
            }
            // we're safe to assume by now the ellipsis fits in the last line up to lastChar
            // but we go further to crunch by word and do some cleanup / nitpicks
            final int origLineStart = getLineStart(lastLine);
            final BreakIterator wb = textView.getWordInstance();
            boolean trailingWhitespacesOnly = lastChar == origLastChar;
            try {
                wb.setText(textView.getText().toString());
                int last = wb.preceding(lastChar);
                if (!wb.isBoundary(lastChar) && last != BreakIterator.DONE) {
                    lastChar = last;
                    trailingWhitespacesOnly = false;
                    last = wb.preceding(lastChar);
                }
                // strip whitespaces: the last line might well end with a space and a line return;
                // be greedy since we're always prefixing the ellipsis to be drawn with a space
                // and we won't want there to end up having more than one
                while (last != BreakIterator.DONE && Character.isWhitespace(
                        Character.codePointAt(textView.getText(), last))) {
                    lastChar = last;
                    last = wb.preceding(lastChar);
                }
            } finally {
                textView.recycle(wb);
            }
            ellipsisLine = lastLine.line;
            ellipsisBaseline = lastLine.baseline;
            ellipsisLTR = lastLine.isLTR;
            // our clipping mask to be, which also positions the ellipsis to be drawn
            // default to zero width bounds to cloak nothing of the text drawn by super.onDraw(),
            // say if we're simply drawing an ellipsis above it when screen estate fits
            final float initialPos = getLineEndEdge(lastLine, 0);
            ellipsisBounds = new RectF(initialPos, lastLine.top, initialPos, lastLine.bottom);
            if (lastChar != origLastChar) {
                // whitespaces stripped above also include line break characters (hard returns)
                // opportunistically wrap the ellipsis to the second last line if screen estate fits
                if (lastChar < origLineStart && lastLine.line > 0) {
                    if (layout.getLineMax(lastLine.line - 1) + ellipsisWidth
                            <= getLineWidth(lastLine)) { // assuming width consistent across lines
                        ellipsisLine -= 1; // shortfall = 0;
                        ellipsisBaseline = layout.getLineBaseline(ellipsisLine);
                        ellipsisLTR = (layout.getParagraphDirection(ellipsisLine)
                                == Layout.DIR_LEFT_TO_RIGHT);
                    } else {
                        // we're not crunching anything above the last line, so never mind
                        // just let the ellipsis start on its own right in the last line
                        lastChar = origLineStart;
                    }
                }
                // finally setting the extent of the clipping mask, we need one after all
                final float ellipsisPos = layout.getPrimaryHorizontal(lastChar);
                setStartEdge(ellipsisBounds, ellipsisPos, ellipsisLTR);
                // optimization: we won't need a mask just for whitespaces on the same last line
                if (trailingWhitespacesOnly && lastChar >= origLineStart) {
                    setEndEdge(ellipsisBounds, ellipsisPos, ellipsisLTR);
                }
            }
            // nitpick: omit the prefixed space if ellipsis starts on its own line
            if (sameStartEdge(ellipsisBounds, lastLineBounds, ellipsisLTR)) {
                if (ellipsisLTR) {
                    ellipsisString = ellipsisString.substring(1);
                } else {
                    ellipsisString = ellipsisString.substring(0, ellipsisString.length() - 1);
                }
            }

            return lastLine;
        }
    }

    /* just a subclass with additional bounds info for debug purposes */
    private static class EllipsizeParamsDebug extends EllipsizeParams {
        public Rect lastLineBounds;
        public RectF lastLineEndBounds;

        EllipsizeParamsDebug(final NewPipeTextView textView) {
            final LineMetrics lastLine = initialize(textView);
            lastLineBounds = getLineBounds(lastLine);
            lastLineEndBounds = getLineRemainderBounds(lastLine);
        }
    }

    /* subclass which proxies functions back to Layout where short-circuits are unimplemented */
    private static class EllipsizeParamsLayout extends EllipsizeParams {
        EllipsizeParamsLayout(final NewPipeTextView textView) {
            initialize(textView);
        }
        @Override
        protected float getLineLeft(final LineMetrics metrics) {
            return getLayout() != null ? getLayout().getLineLeft(metrics.line)
                    : super.getLineLeft(metrics);
        }
        @Override
        protected float getLineRight(final LineMetrics metrics) {
            return getLayout() != null ? getLayout().getLineRight(metrics.line)
                    : super.getLineRight(metrics);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Word breaker utility
    //////////////////////////////////////////////////////////////////////////*/

    /* tries to do some recycle magic here */
    protected BreakIterator getWordInstance() {
        if (oldWordIterator == null || oldWordIterator.get() == null) {
            return BreakIterator.getWordInstance();
        } else {
            return oldWordIterator.get();
        }
    }
    protected void recycle(final BreakIterator wordIterator) {
        if (wordIterator != null) {
            oldWordIterator = new WeakReference<>(wordIterator);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // TextView stuff
    //////////////////////////////////////////////////////////////////////////*/

    private EllipsizeParams ellipsizeParams;
    private WeakReference<BreakIterator> oldWordIterator;

    /* used in expanding/collapsing animation; 0 for ellipsis to be invisible, 1 fully visible */
    private float crossfadeEllipsis = -1; // not in use (not animating)

    private static void clipOutRectCompat(final Canvas canvas, final RectF rect) {
        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutRect(rect);
        } else {
            canvas.clipRect(rect, android.graphics.Region.Op.DIFFERENCE);
        }
    }

    /* The bone of this override is to carry out our custom ellipsizing which comprises
     * two components: a clipping mask and an ellipsis, backed by an EllipsizeParams
     * (a super-lazy initialized home-grown cache which stores the necessary metrics).
     * Much like a magician's trick, the mask cloaks part of the ellipisized text
     * while the ellipsis is drawn above it, as necessary. */
    @Override
    protected void onDraw(final Canvas canvas) {
        final Layout layout = getLayout();
        if ((layout == null || getMaxLines() == -1 || getLineCount() == 0 || ellipsisState() != 1)
                && crossfadeEllipsis == -1) {
            super.onDraw(canvas);
            return;
        }

        // invalidate cache if text layout changed;
        // except when animation is in flight: we actually reuse the last cached ellipsizeParams
        if (ellipsizeParams != null && crossfadeEllipsis == -1) {
            // There doesn't seem to be a good way to detect (the internal) text layout changes
            // eg layout change listeners are only fired upon external (size) changes but not, say,
            // an internal text reflow (invalidated internally by setText() and other functions).
            // We cheated a bit by keeping a WeakReference to the TextView's internal Layout
            // at the time EllipsizeParams is created and computed leveraging the fact that
            // getLayout() returns the instance rather than a copy of it.
            if (ellipsizeParams.getLayout() != getLayout()) {
                ellipsizeParams = null;
                if (DEBUG) {
                    canvas.drawColor(0x22222222);
                }
            }
        }
        // super lazy initialize our ellipsizeParams cache
        if (ellipsizeParams == null) {
            if (crossfadeEllipsis == -1) { // ie we're not drawing amidst an animation
                // We're not left with much better place to do computations:
                // we'll be fiddling with setMaxLines() to measure() the final bounds post-animation
                // so tapping into onMeasure() might incur extra computation cycles when we're not
                // really drawing (given our limitation in listening to text layout changes above).
                // Computations in onDraw() is generally a bad idea after all;
                // so we roll our home-grown cache as part of the remedy.
                ellipsizeParams = EllipsizeParams.newInstance(this);
            } else {
                // we're in flight an animation but without the previously cached ellipsizeParams
                // ah well, never mind - not animating the ellipsis change this time
                super.onDraw(canvas);
                return;
            }
        }
        canvas.save();
        final Paint p = new Paint(layout.getPaint());
        final int offsetX = getTotalPaddingLeft();
        final int offsetY = getTotalPaddingTop();
        canvas.translate(offsetX, offsetY);
        try {
            if (DEBUG && ellipsizeParams instanceof EllipsizeParamsDebug
                    && crossfadeEllipsis == -1) {
                final EllipsizeParamsDebug debugParams = (EllipsizeParamsDebug) ellipsizeParams;
                p.setARGB(100, 255, 0, 0);
                canvas.drawRect(debugParams.lastLineBounds, p);

                p.setARGB(100, 0, 255, 0);
                canvas.drawRect(debugParams.lastLineEndBounds, p);
            }

            if (DEBUG && ellipsizeParams.ellipsisBounds.width() > 0) {
                p.setARGB(100, 0, 0, 255);
                canvas.drawRect(ellipsizeParams.ellipsisBounds, p);
            }

            if (crossfadeEllipsis > 0 || ellipsisState() == 1) {
                RectF clipOutBounds = null;

                p.setColor(getCurrentTextColor());
                if (crossfadeEllipsis != -1) { // animation stuff
                    // simulate sliding window to the end of thd ellipsized last line
                    clipOutBounds = new RectF(ellipsizeParams.ellipsisBounds);
                    offsetEndEdge(clipOutBounds, ellipsizeParams.ellipsisLTR,
                            -clipOutBounds.width() * crossfadeEllipsis);
                    canvas.save();
                    clipOutRectCompat(canvas, clipOutBounds);
                    p.setAlpha((int) ((float) 255 * crossfadeEllipsis));
                }
                p.setTextAlign(ellipsizeParams.ellipsisLTR ? Paint.Align.LEFT : Paint.Align.RIGHT);
                canvas.drawText(ellipsizeParams.ellipsisString,
                        getStartEdge(ellipsizeParams.ellipsisBounds, ellipsizeParams.ellipsisLTR),
                        (float) (getTotalPaddingTop() + ellipsizeParams.ellipsisBaseline), p);
                if (clipOutBounds != null) {
                    canvas.restore();
                    clipOutBounds = new RectF(ellipsizeParams.ellipsisBounds);
                    offsetStartEdge(clipOutBounds, ellipsizeParams.ellipsisLTR,
                            clipOutBounds.width() * (1 - crossfadeEllipsis));
                    clipOutRectCompat(canvas, clipOutBounds);
                } else if (ellipsizeParams.ellipsisBounds.width() > 0) {
                    clipOutRectCompat(canvas, ellipsizeParams.ellipsisBounds);
                }
            }

        } finally {
            canvas.translate(-offsetX, -offsetY);
        }
        super.onDraw(canvas);
        canvas.restore();
    }
}
