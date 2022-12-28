package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

import org.schabi.newpipe.util.AnimationUtil;
import org.schabi.newpipe.util.EllipsizeParams;
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

    public int getDisplayLines() {
        return getMaxLines() == -1 ? getLineCount() : Math.min(getMaxLines(), getLineCount());
    }

    /* the transient ellipsizing state at the current point in time */
    @EllipsisState
    public int ellipsisState() {
        if (getLayout() == null) {
            return EllipsisState.UNDETERMINED;
        }
        if (getLayout().getLineEnd(getDisplayLines() - 1) != getText().length()) {
            return EllipsisState.EXPANDABLE;
        } else {
            return getLayout().getEllipsisCount(getDisplayLines() - 1) > 0
                    ? EllipsisState.ELLIPSIZED : EllipsisState.FULL_VIEW;
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
        if ((ellipsisState() == EllipsisState.EXPANDABLE && getMaxLines() < expandedLines)
                || getMaxLines() > collapsedLines) {

            final boolean isCollapsing = getMaxLines() > collapsedLines;
            AnimationUtil.toggle(this,
                    isCollapsing ? collapseDuration : expandDuration,
                    isCollapsing ? collapsedLines : expandedLines,
                    isCollapsing, this);
        }
    }
    /* implements AnimationUtil.OnAnimateListener */
    @Override
    public void onAnimateProgress(final float animatedFraction, final boolean isCollapsing) {
        // determine position of the sliding window for the ellipsis during animation
        if (ellipsizeParams != null) {
            // afford to lose some precision and smuggle it into 8 bits (0 - 255) for storage
            EllipsizeParams.setByte(ellipsizeParams, EllipsizeParams.CROSSFADE_ELLIPSIS,
                    (int) (255 * (isCollapsing ? animatedFraction : 1 - animatedFraction)));
        }
    }
    @Override
    public void onAnimationStart(final View v, final boolean isCollapsing) {
        if (ellipsizeParams != null) {
            EllipsizeParams.setFlag(ellipsizeParams, EllipsizeParams.ANIMATING, true);
        }
    }
    @Override
    public void onAnimationEnd(final View v, final boolean reversed, final boolean expanded) {
        if (ellipsizeParams != null) {
            EllipsizeParams.setFlag(ellipsizeParams, EllipsizeParams.ANIMATING, false);
        }
        if (!reversed && onToggleListener != null) {
            onToggleListener.onToggle(this, expanded);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Static members
    //////////////////////////////////////////////////////////////////////////*/

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EllipsisState.UNDETERMINED, EllipsisState.FULL_VIEW,
            EllipsisState.EXPANDABLE, EllipsisState.ELLIPSIZED})
    public @interface EllipsisState {
        int UNDETERMINED = -1; // usually before the layout is first drawn
        int FULL_VIEW    = 0;
        int EXPANDABLE   = 1;  // we determine the text is shown in part and thus expandable
        int ELLIPSIZED   = 2;  // TextView reports the text is ellipsized (by it)
    }


    /*//////////////////////////////////////////////////////////////////////////
    // TextView stuff
    //////////////////////////////////////////////////////////////////////////*/

    // weak reference to the Layout upon which ellipsizeParams were drawn up
    private WeakReference<Layout> ellipsizeLayout;

    // poor man's all in one briefcase
    private int[] ellipsizeParams;

    @SuppressWarnings("deprecation")
    private static void clipOutRectCompat(@NonNull final Canvas canvas, final float left,
                                          final float top, final float right, final float bottom) {
        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutRect(left, top, right, bottom);
        } else {
            canvas.clipRect(left, top, right, bottom, android.graphics.Region.Op.DIFFERENCE);
        }
    }
    // shorthands to fetch ellipsising params from config storage
    private boolean getConfigFlag(final int flag) {
        return EllipsizeParams.getFlag(ellipsizeParams, flag);
    }
    private int getConfigByte(final int param) {
        return EllipsizeParams.getByte(ellipsizeParams, param);
    }
    private int getConfigInt(final int param) {
        return EllipsizeParams.getInt(ellipsizeParams, param);
    }
    private float getConfigFloat(final int param) {
        return EllipsizeParams.getFloat(ellipsizeParams, param);
    }
    private boolean textLayoutChanged() {
        // There doesn't seem to be a good way to detect (the internal) text layout changes
        // eg layout change listeners only fire upon external (size) changes but not say, an
        // internal text reflow (invalidated internally by setText() and other functions).
        // We cheated a bit by keeping a WeakReference to the TextView's internal Layout
        // at the time EllipsizeParams is created and computed leveraging the fact that
        // getLayout() returns the instance rather than a copy of it.
        return ellipsizeLayout != null && ellipsizeLayout.get() != getLayout();
    }
    private void initializeEllipsizeParams() {
        ellipsizeLayout = new WeakReference<>(getLayout());
        ellipsizeParams = EllipsizeParams.initialize(this, ellipsizeParams);
    }

    /* The bone of this override is to carry out our custom ellipsizing which comprises
     * two components: a clipping mask and an ellipsis, backed by an EllipsizeParams
     * (a super-lazy initialized home-grown cache which stores the necessary metrics).
     * Much like a magician's trick, the mask cloaks part of the ellipisized text
     * while the ellipsis is drawn above it, as necessary. */
    @Override
    protected void onDraw(final Canvas canvas) {
        final Layout layout = getLayout();
        final boolean animating = ellipsizeParams != null
                && getConfigFlag(EllipsizeParams.ANIMATING);
        if (layout == null || getLineCount() == 0
                || (ellipsisState() != EllipsisState.EXPANDABLE && !animating)) {
            super.onDraw(canvas);
            return;
        }

        // invalidate cache if text layout changed
        // except when animation is in flight: we actually reuse the last cached ellipsizeParams
        if (ellipsizeParams != null && !animating && textLayoutChanged()) {
            EllipsizeParams.setFlag(ellipsizeParams, EllipsizeParams.INITIALIZED, false);
            if (getConfigFlag(EllipsizeParams.SCHEMA_DEBUG)) {
                canvas.drawColor(0x22222222);
            }
        }
        // super lazy initialize our ellipsizeParams cache
        if (ellipsizeParams == null || !getConfigFlag(EllipsizeParams.INITIALIZED)) {
            if (!animating) {
                // We're not left with much better place to do computations:
                // we'll be fiddling with setMaxLines() to measure() the final bounds post-animation
                // so tapping into onMeasure() might incur extra computation cycles when we're not
                // really drawing (given our limitation in listening to text layout changes above).
                // Computations in onDraw() is generally a bad idea after all,
                // so we roll our home-grown cache as part of the remedy.
                initializeEllipsizeParams();
            } else {
                // we're in flight an animation but without the previously cached ellipsizeParams
                // ah well, never mind - not animating the ellipsis change this time
                super.onDraw(canvas);
                return;
            }
        }
        canvas.save();
        // the docs advise against 'messing with' the Layout's Paint; Android-Lint advises against
        // "object allocations during draw/layout operations": perhaps we could just borrow
        // the Layout's Paint and revert our changes afterwards to get the best of two worlds
        final Paint p = layout.getPaint();
        final int offsetX = getTotalPaddingLeft();
        final int offsetY = getTotalPaddingTop();
        canvas.translate(offsetX, offsetY);

        final boolean debug = getConfigFlag(EllipsizeParams.SCHEMA_DEBUG);
        final boolean isLTR = getConfigFlag(EllipsizeParams.IS_LTR);
        if (debug && !animating) {
            // draw lastLineBounds
            p.setARGB(100, 255, 0, 0);
            canvas.drawRect(0, getConfigInt(EllipsizeParams.LL_TOP),
                    getConfigInt(EllipsizeParams.LL_WIDTH),
                    getConfigInt(EllipsizeParams.LL_BOTTOM), p);
            // draw lastLineEndBounds
            p.setARGB(100, 0, 255, 0);
            canvas.drawRect(isLTR ? getConfigFloat(EllipsizeParams.LE_EDGE) : 0,
                    getConfigInt(EllipsizeParams.LL_TOP),
                    getConfigFloat(isLTR ? EllipsizeParams.LL_WIDTH : EllipsizeParams.LE_EDGE),
                    getConfigInt(EllipsizeParams.LL_BOTTOM), p);
        }

        if (debug && (getConfigFloat(EllipsizeParams.EB_RIGHT_D)
                - getConfigFloat(EllipsizeParams.EB_LEFT_D)) > 0) {
            p.setARGB(100, 0, 0, 255);
            canvas.drawRect(getConfigFloat(EllipsizeParams.EB_LEFT_D),
                    getConfigFloat(EllipsizeParams.EB_TOP_D),
                    getConfigFloat(EllipsizeParams.EB_RIGHT_D),
                    getConfigFloat(EllipsizeParams.EB_BOTTOM_D), p);
        }

        if (animating || ellipsisState() == EllipsisState.EXPANDABLE) {
            final int crossfadeEllipsis = getConfigByte(EllipsizeParams.CROSSFADE_ELLIPSIS);
            float splitPt = -1;
            final float clipOutBoundsL = getConfigFloat(
                    debug ? EllipsizeParams.EB_LEFT_D : EllipsizeParams.EB_LEFT);
            final float clipOutBoundsT = getConfigFloat(
                    debug ? EllipsizeParams.EB_TOP_D : EllipsizeParams.EB_TOP);
            final float clipOutBoundsR = getConfigFloat(
                    debug ? EllipsizeParams.EB_RIGHT_D : EllipsizeParams.EB_RIGHT);
            final float clipOutBoundsB = getConfigFloat(
                    debug ? EllipsizeParams.EB_BOTTOM_D : EllipsizeParams.EB_BOTTOM);

            p.setColor(getCurrentTextColor());
            if (animating) { // animation stuff
                // simulate sliding window to the end of the ellipsized last line
                splitPt = (clipOutBoundsR - clipOutBoundsL) * crossfadeEllipsis / 255;
                canvas.save();
                // pass in the four corners as parameters to avoid boxing an intermediate RectF
                clipOutRectCompat(canvas,
                        isLTR ? clipOutBoundsL : clipOutBoundsL + splitPt, clipOutBoundsT,
                        isLTR ? clipOutBoundsR - splitPt : clipOutBoundsR, clipOutBoundsB);
                p.setAlpha(crossfadeEllipsis);
            }
            final Paint.Align origAlign = p.getTextAlign();
            p.setTextAlign(isLTR ? Paint.Align.LEFT : Paint.Align.RIGHT);
            final boolean skipSpace = getConfigFlag(EllipsizeParams.SKIP_PREFIX_SPACE);
            canvas.drawText(EllipsizeParams.ELLIPSIS_CHARS, isLTR && !skipSpace ? 0 : 1,
                    skipSpace ? EllipsizeParams.ELLIPSIS_LEN - 1 : EllipsizeParams.ELLIPSIS_LEN,
                    isLTR ? clipOutBoundsL : clipOutBoundsR,
                    getTotalPaddingTop() + getConfigFloat(debug
                            ? EllipsizeParams.EB_BASELINE_D : EllipsizeParams.EB_BASELINE), p);
            p.setTextAlign(origAlign);
            if (animating) {
                canvas.restore();
                clipOutRectCompat(canvas,
                        isLTR ? clipOutBoundsR - splitPt : clipOutBoundsL, clipOutBoundsT,
                        isLTR ? clipOutBoundsR : clipOutBoundsL + splitPt, clipOutBoundsB);
            } else if (clipOutBoundsR - clipOutBoundsL > 0) {
                clipOutRectCompat(canvas,
                        clipOutBoundsL, clipOutBoundsT, clipOutBoundsR, clipOutBoundsB);
            }
        }

        canvas.translate(-offsetX, -offsetY);
        super.onDraw(canvas);
        canvas.restore();
    }
}
