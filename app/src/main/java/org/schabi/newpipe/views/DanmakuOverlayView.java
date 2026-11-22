/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-FileCopyrightText: 2026 PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight danmaku/bullet-comment renderer used by the video player.
 *
 * Scrolling comments are assigned to the first free row and fixed top/bottom comments use
 * independent row cursors. The view is deliberately non-interactive so player gestures continue
 * to reach the normal player root.
 */
public final class DanmakuOverlayView extends FrameLayout {
    private final List<Animator> activeAnimators = new ArrayList<>();
    private long[] rowAvailableAt = new long[0];
    private int topRowCursor;
    private int bottomRowCursor;

    public DanmakuOverlayView(@NonNull final Context context) {
        super(context);
        init();
    }

    public DanmakuOverlayView(@NonNull final Context context,
                              @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DanmakuOverlayView(@NonNull final Context context,
                              @Nullable final AttributeSet attrs,
                              final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(true);
        setClipToPadding(true);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void showComments(@NonNull final List<BulletCommentsInfoItem> comments) {
        if (comments.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        final int maxRows = clamp(
                prefs.getInt(getContext().getString(R.string.danmaku_max_rows_key), 6),
                1,
                12);
        final int textSizeSp = clamp(
                prefs.getInt(getContext().getString(R.string.danmaku_text_size_key), 18),
                12,
                32);
        final int opacityPercent = clamp(
                prefs.getInt(getContext().getString(R.string.danmaku_opacity_key), 85),
                10,
                100);
        final int durationMs = clamp(
                prefs.getInt(getContext().getString(R.string.danmaku_duration_key), 8),
                4,
                15) * 1000;
        final boolean showNames = prefs.getBoolean(
                getContext().getString(R.string.danmaku_show_usernames_key), false);
        final boolean interactive = prefs.getBoolean(
                getContext().getString(R.string.danmaku_hold_drag_key), false);

        if (rowAvailableAt.length != maxRows) {
            rowAvailableAt = new long[maxRows];
            topRowCursor = 0;
            bottomRowCursor = 0;
        }

        for (final BulletCommentsInfoItem item : comments) {
            if (item == null || item.getCommentText() == null
                    || item.getCommentText().isBlank()) {
                continue;
            }
            drawComment(item, textSizeSp, opacityPercent, durationMs, maxRows,
                    showNames, interactive);
        }
    }

    private void drawComment(@NonNull final BulletCommentsInfoItem item,
                             final int textSizeSp,
                             final int opacityPercent,
                             final int durationMs,
                             final int maxRows,
                             final boolean showNames,
                             final boolean interactive) {
        final TextView text = new TextView(getContext());
        text.setText(DanmakuTextFormatter.format(
                item.getName(), item.getCommentText(), showNames));
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                (float) (textSizeSp * Math.max(0.75, item.getRelativeFontSize())));
        final int commentColor = item.getArgbColor() == 0
                ? Color.WHITE
                : item.getArgbColor();
        text.setTextColor(withOpacity(commentColor, opacityPercent));
        text.setShadowLayer(2.0f, 0.0f, 0.0f, withOpacity(Color.BLACK, opacityPercent));
        if (item.getPosition() == BulletCommentsInfoItem.Position.SUPERCHAT) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }

        final LayoutParams params = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        addView(text, params);

        text.post(() -> {
            if (text.getParent() == null) {
                return;
            }

            final int row = rowFor(item.getPosition(), maxRows, durationMs);
            final int rowHeight = Math.max(1, getHeight() / maxRows);
            final float y = Math.max(0,
                    Math.min(getHeight() - text.getHeight(), row * rowHeight));
            text.setY(y);

            final Animator animator;
            if (item.getPosition() == BulletCommentsInfoItem.Position.REGULAR) {
                text.setX(getWidth());
                animator = ObjectAnimator.ofFloat(
                        text,
                        View.TRANSLATION_X,
                        getWidth(),
                        -text.getWidth());
            } else {
                text.setX((getWidth() - text.getWidth()) / 2.0f);
                animator = ObjectAnimator.ofFloat(text, View.ALPHA, 1.0f, 1.0f);
            }

            animator.setDuration(item.getLastingTime() > 0
                    ? item.getLastingTime()
                    : durationMs);
            animator.setInterpolator(new LinearInterpolator());
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(final Animator animation) {
                    activeAnimators.remove(animation);
                    removeView(text);
                }

                @Override
                public void onAnimationCancel(final Animator animation) {
                    activeAnimators.remove(animation);
                    removeView(text);
                }
            });
            activeAnimators.add(animator);
            if (interactive) {
                enableBubbleInteraction(text, item, animator);
            }
            animator.start();
        });
    }

    /**
     * Opt-in touch interaction pauses the chosen bubble while pressed. Dragging it changes its
     * vertical position; holding long enough copies the full message including its author.
     *
     * @param text moving message view
     * @param item original comment
     * @param animator movement animation
     */
    private void enableBubbleInteraction(@NonNull final TextView text,
                                         @NonNull final BulletCommentsInfoItem item,
                                         @NonNull final Animator animator) {
        final String copied = DanmakuTextFormatter.format(
                item.getName(), item.getCommentText(), true);
        text.setClickable(true);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        text.setContentDescription(copied);
        text.setOnClickListener(view -> { });
        text.setOnTouchListener(new View.OnTouchListener() {
            private float previousRawY;

            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        previousRawY = event.getRawY();
                        if (animator.isStarted() && !animator.isPaused()) {
                            animator.pause();
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        final float movement = event.getRawY() - previousRawY;
                        previousRawY = event.getRawY();
                        final float dragged = text.getTranslationY() + movement;
                        final float maxY = Math.max(0, getHeight() - text.getHeight());
                        text.setTranslationY(Math.max(0, Math.min(maxY, dragged)));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (event.getEventTime() - event.getDownTime()
                                >= ViewConfiguration.getLongPressTimeout()) {
                            final ClipboardManager clipboard = (ClipboardManager)
                                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText(
                                        getContext().getString(R.string.danmaku_copied), copied));
                                Toast.makeText(getContext(), R.string.danmaku_copied,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                        view.performClick();
                        if (animator.isPaused()) {
                            animator.resume();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (animator.isPaused()) {
                            animator.resume();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private int rowFor(@Nullable final BulletCommentsInfoItem.Position position,
                       final int maxRows,
                       final int durationMs) {
        if (position == BulletCommentsInfoItem.Position.TOP
                || position == BulletCommentsInfoItem.Position.SUPERCHAT) {
            final int row = topRowCursor % maxRows;
            topRowCursor++;
            return row;
        }
        if (position == BulletCommentsInfoItem.Position.BOTTOM) {
            final int row = maxRows - 1 - (bottomRowCursor % maxRows);
            bottomRowCursor++;
            return row;
        }

        final long now = System.currentTimeMillis();
        int selected = 0;
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < rowAvailableAt.length; i++) {
            if (rowAvailableAt[i] <= now) {
                selected = i;
                earliest = Long.MIN_VALUE;
                break;
            }
            if (rowAvailableAt[i] < earliest) {
                earliest = rowAvailableAt[i];
                selected = i;
            }
        }
        rowAvailableAt[selected] = now + Math.max(750L, durationMs / 3L);
        return selected;
    }

    public void pauseComments() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        for (final Animator animator : new ArrayList<>(activeAnimators)) {
            if (animator.isStarted() && !animator.isPaused()) {
                animator.pause();
            }
        }
    }

    public void resumeComments() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        for (final Animator animator : new ArrayList<>(activeAnimators)) {
            if (animator.isPaused()) {
                animator.resume();
            }
        }
    }

    public void clearComments() {
        for (final Animator animator : new ArrayList<>(activeAnimators)) {
            animator.cancel();
        }
        activeAnimators.clear();
        removeAllViews();
        Arrays.fill(rowAvailableAt, 0L);
        topRowCursor = 0;
        bottomRowCursor = 0;
    }

    static int withOpacity(final int argb, final int opacityPercent) {
        final int alpha = Math.round(255.0f * clamp(opacityPercent, 0, 100) / 100.0f);
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
