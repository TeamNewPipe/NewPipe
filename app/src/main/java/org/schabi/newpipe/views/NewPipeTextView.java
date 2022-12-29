package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
                && EllipsizeParams.getFlag(ellipsizeParams, EllipsizeParams.ANIMATING);
        if (layout == null || getLineCount() == 0
                || (ellipsisState() != EllipsisState.EXPANDABLE && !animating)) {
            super.onDraw(canvas);
            return;
        }

        // invalidate cache if text layout changed
        // except when animation is in flight: we actually reuse the last cached ellipsizeParams
        if (ellipsizeParams != null && !animating && textLayoutChanged()) {
            EllipsizeParams.setFlag(ellipsizeParams, EllipsizeParams.INITIALIZED, false);
            if (EllipsizeParams.getFlag(ellipsizeParams, EllipsizeParams.SCHEMA_DEBUG)) {
                canvas.drawColor(0x22222222);
            }
        }
        // super lazy initialize our ellipsizeParams cache
        if (ellipsizeParams == null
                || !EllipsizeParams.getFlag(ellipsizeParams, EllipsizeParams.INITIALIZED)) {
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

        EllipsizeParams.onDraw(canvas, p, ellipsizeParams, ellipsisState(), getCurrentTextColor());

        canvas.translate(-offsetX, -offsetY);
        super.onDraw(canvas);
        canvas.restore();
    }
}
