package org.schabi.newpipe.local.history;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import org.schabi.newpipe.R;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class HistoryDateFastScroller extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawingRect = new RectF();

    private final float trackWidth;
    private final float thumbWidth;
    private final float thumbHeight;
    private final float verticalInset;
    private final float edgeInset;
    private final int bubbleGap;
    private final int bubblePaddingHorizontal;
    private final int bubblePaddingVertical;

    private int itemCount;
    private int position;
    private boolean dragging;
    @Nullable
    private IntConsumer onPositionChangedListener;
    @Nullable
    private IntFunction<String> labelProvider;
    @Nullable
    private Consumer<Boolean> onDragStateChangedListener;
    @Nullable
    private PopupWindow bubblePopup;
    @Nullable
    private TextView bubbleText;

    public HistoryDateFastScroller(@NonNull final Context context) {
        this(context, null);
    }

    public HistoryDateFastScroller(@NonNull final Context context,
                                   @Nullable final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HistoryDateFastScroller(@NonNull final Context context,
                                   @Nullable final AttributeSet attrs,
                                   final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackWidth = dp(3);
        thumbWidth = dp(8);
        thumbHeight = dp(36);
        verticalInset = dp(18);
        edgeInset = dp(8);
        bubbleGap = Math.round(dp(8));
        bubblePaddingHorizontal = Math.round(dp(16));
        bubblePaddingVertical = Math.round(dp(10));

        trackPaint.setColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOutline, Color.GRAY));
        trackPaint.setAlpha(110);
        thumbPaint.setColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorPrimary, Color.DKGRAY));

        setClickable(true);
        setFocusable(true);
    }

    public void setOnPositionChangedListener(
            @Nullable final IntConsumer onPositionChangedListener) {
        this.onPositionChangedListener = onPositionChangedListener;
    }

    public void setLabelProvider(@Nullable final IntFunction<String> labelProvider) {
        this.labelProvider = labelProvider;
        updateAccessibilityDescription();
    }

    public void setOnDragStateChangedListener(
            @Nullable final Consumer<Boolean> onDragStateChangedListener) {
        this.onDragStateChangedListener = onDragStateChangedListener;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setItemCount(final int itemCount) {
        this.itemCount = Math.max(0, itemCount);
        setPosition(Math.min(position, Math.max(0, this.itemCount - 1)));
        setEnabled(this.itemCount > 1);
    }

    public void setPosition(final int position) {
        if (itemCount <= 1) {
            this.position = 0;
        } else {
            this.position = Math.max(0, Math.min(position, itemCount - 1));
        }
        invalidate();
        updateAccessibilityDescription();
        if (dragging) {
            updateBubble();
        }
    }

    public void dismissBubble() {
        setDragging(false);
        if (bubblePopup != null) {
            bubblePopup.dismiss();
        }
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (itemCount <= 1) {
            return;
        }

        final float centerX = getTrackCenterX();
        final float top = verticalInset;
        final float bottom = Math.max(top, getHeight() - verticalInset);
        drawingRect.set(centerX - trackWidth / 2f, top,
                centerX + trackWidth / 2f, bottom);
        canvas.drawRoundRect(drawingRect, trackWidth / 2f, trackWidth / 2f, trackPaint);

        final float thumbCenterY = top + getFraction() * (bottom - top);
        drawingRect.set(centerX - thumbWidth / 2f, thumbCenterY - thumbHeight / 2f,
                centerX + thumbWidth / 2f, thumbCenterY + thumbHeight / 2f);
        canvas.drawRoundRect(drawingRect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent event) {
        if (!isEnabled() || itemCount <= 1) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!FastScrollerTouchTarget.INSTANCE.isOnThumb(
                        event.getX(), event.getY(), getTrackCenterX(), getThumbCenterY(),
                        dp(16), thumbHeight / 2f + dp(12))) {
                    return false;
                }
                setDragging(true);
                requestParentDisallowIntercept(true);
                updateFromTouch(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                updateFromTouch(event.getY());
                requestParentDisallowIntercept(false);
                dismissBubble();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowIntercept(false);
                dismissBubble();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        final String label = getLabel();
        if (!label.isEmpty()) {
            announceForAccessibility(label);
        }
        return true;
    }

    @Override
    public boolean onKeyDown(final int keyCode, @NonNull final KeyEvent event) {
        if (!isEnabled() || itemCount <= 1) {
            return super.onKeyDown(keyCode, event);
        }

        final int step = Math.max(1, itemCount / 20);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            moveTo(position - step);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveTo(position + step);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissBubble();
        super.onDetachedFromWindow();
    }

    private void setDragging(final boolean dragging) {
        if (this.dragging == dragging) {
            return;
        }
        this.dragging = dragging;
        if (onDragStateChangedListener != null) {
            onDragStateChangedListener.accept(dragging);
        }
    }

    private void requestParentDisallowIntercept(final boolean disallowIntercept) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private void updateFromTouch(final float y) {
        final float availableHeight = Math.max(1f, getHeight() - 2f * verticalInset);
        final float fraction = Math.max(0f,
                Math.min(1f, (y - verticalInset) / availableHeight));
        moveTo(Math.round(fraction * (itemCount - 1)));
        updateBubble();
    }

    private void moveTo(final int targetPosition) {
        final int boundedPosition = Math.max(0, Math.min(targetPosition, itemCount - 1));
        if (boundedPosition == position) {
            return;
        }
        position = boundedPosition;
        invalidate();
        updateAccessibilityDescription();
        if (onPositionChangedListener != null) {
            onPositionChangedListener.accept(position);
        }
    }

    private float getTrackCenterX() {
        return getTrackCenterXForLayoutDirection(getLayoutDirection());
    }

    float getTrackCenterXForLayoutDirection(final int layoutDirection) {
        final float minimumCenter = thumbWidth / 2f;
        final float insetCenter = Math.max(minimumCenter,
                Math.min(edgeInset, getWidth() - minimumCenter));
        return layoutDirection == LAYOUT_DIRECTION_RTL
                ? insetCenter : getWidth() - insetCenter;
    }

    private float getThumbCenterY() {
        final float travel = Math.max(1f, getHeight() - 2f * verticalInset);
        return verticalInset + getFraction() * travel;
    }

    private float getFraction() {
        return itemCount <= 1 ? 0f : position / (float) (itemCount - 1);
    }

    @NonNull
    private String getLabel() {
        if (labelProvider == null || itemCount == 0) {
            return "";
        }
        final String label = labelProvider.apply(position);
        return label == null ? "" : label;
    }

    private void updateAccessibilityDescription() {
        final String label = getLabel();
        if (!label.isEmpty()) {
            setContentDescription(getContext().getString(
                    R.string.history_date_fast_scroll_description, label));
        }
    }

    private void updateBubble() {
        final String label = getLabel();
        if (label.isEmpty() || !isAttachedToWindow()) {
            return;
        }
        ensureBubble();
        bubbleText.setText(label);
        bubbleText.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        final int bubbleWidth = bubbleText.getMeasuredWidth();
        final int bubbleHeight = bubbleText.getMeasuredHeight();
        final int[] location = new int[2];
        getLocationOnScreen(location);

        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final float trackCenterX = getTrackCenterX();
        int x = rtl
                ? Math.round(location[0] + trackCenterX + thumbWidth / 2f + bubbleGap)
                : Math.round(location[0] + trackCenterX - thumbWidth / 2f
                        - bubbleGap - bubbleWidth);
        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        x = Math.max(0, Math.min(x, screenWidth - bubbleWidth));

        final float thumbCenterY = verticalInset
                + getFraction() * Math.max(1f, getHeight() - 2f * verticalInset);
        final int y = Math.max(0,
                Math.round(location[1] + thumbCenterY - bubbleHeight / 2f));

        if (bubblePopup.isShowing()) {
            bubblePopup.update(x, y, bubbleWidth, bubbleHeight);
        } else {
            bubblePopup.setWidth(bubbleWidth);
            bubblePopup.setHeight(bubbleHeight);
            bubblePopup.showAtLocation(getRootView(), Gravity.NO_GRAVITY, x, y);
        }
    }

    private void ensureBubble() {
        if (bubblePopup != null) {
            return;
        }

        bubbleText = new TextView(getContext());
        bubbleText.setGravity(Gravity.CENTER);
        bubbleText.setPadding(bubblePaddingHorizontal, bubblePaddingVertical,
                bubblePaddingHorizontal, bubblePaddingVertical);
        bubbleText.setTextColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        bubbleText.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);

        final GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurface, Color.DKGRAY));
        background.setCornerRadius(dp(16));
        bubbleText.setBackground(background);

        bubblePopup = new PopupWindow(bubbleText,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false);
        bubblePopup.setTouchable(false);
        bubblePopup.setClippingEnabled(true);
        bubblePopup.setElevation(dp(6));
    }

    private float dp(final float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
