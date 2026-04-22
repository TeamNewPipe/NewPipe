package org.schabi.newpipe.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import androidx.preference.PreferenceManager;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.BulletCommentsPlayerBinding;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public final class BulletCommentsView extends ConstraintLayout {
    private final String TAG = "BulletCommentsView";
    private SharedPreferences prefs;

    /**
     * Tuple of TextView and ObjectAnimator.
     */
    private static class AnimatedTextView {
        AnimatedTextView(final TextView textView, final ObjectAnimator animator) {
            this.textView = textView;
            this.animator = animator;
        }

        public final TextView textView;
        public final ObjectAnimator animator;
    }

    public BulletCommentsView(final Context context) {
        super(context);
        setClipChildren(false);
        init(context);
    }

    public BulletCommentsView(final Context context,
                              final AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        init(context);
    }

    public BulletCommentsView(final Context context,
                              final AttributeSet attrs,
                              final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(false);
        init(context);
    }

    private void init(final Context context) {
        final View layout = LayoutInflater.from(context)
                .inflate(R.layout.bullet_comments_player, this);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        commentsDuration = prefs.getInt(
                context.getString(R.string.top_bottom_bullet_comments_duration_key), 8);
        durationFactor = (float) prefs.getInt(
                context.getString(R.string.regular_bullet_comments_duration_key), 8)
                / (float) commentsDuration;
        outlineRadius = prefs.getInt(
                context.getString(R.string.bullet_comments_outline_radius_key), 2);

        final boolean limitMaxRows = prefs.getBoolean(
                context.getString(R.string.enable_max_rows_customization_key), false);
        if (limitMaxRows) {
            maxRowsTop = prefs.getInt(
                    context.getString(R.string.max_bullet_comments_rows_top_key), 15);
            maxRowsBottom = prefs.getInt(
                    context.getString(R.string.max_bullet_comments_rows_bottom_key), 15);
            maxRowsRegular = prefs.getInt(
                    context.getString(R.string.max_bullet_comments_rows_regular_key), 15);
        }

        font = prefs.getString(context.getString(R.string.bullet_comments_font_key), "default");
        opacity = prefs.getInt(context.getString(R.string.bullet_comments_opacity_key), 0xFF);
        binding = BulletCommentsPlayerBinding.bind(this);
    }

    private boolean layoutSet = false;

    private void setLayout() {
        final int additionalWidth = additionalSpaceRelative * getWidth();
        binding.bottomRight.getLayoutParams().width = additionalWidth;
        requestLayout();
        Log.i(TAG, "Additional width: " + additionalWidth
                + ", container width: " + binding.bulletCommentsContainer.getWidth());
    }

    private BulletCommentsPlayerBinding binding;
    private final int additionalSpaceRelative = 4;

    private final int commentsRowsCount = 11;
    private int lastCalculatedCommentsRowsCount = 11;
    private List<Long> rows = Collections.synchronizedList(new ArrayList<Long>());
    private List<Map.Entry<Long, Integer>> rowsRegular =
            Collections.synchronizedList(new ArrayList<>());
    private final double commentRelativeTextSize = 1 / 13.5;
    private PriorityQueue<BulletCommentsInfoItem> bulletCommentsInfoItemRegularPool =
            new PriorityQueue<>();
    private PriorityQueue<BulletCommentsInfoItem> bulletCommentsInfoItemFixedPool =
            new PriorityQueue<>();

    private int commentsDuration;
    private float durationFactor;
    private int outlineRadius;
    private String font;
    private int opacity; // 0~255, 0: hide
    private final List<AnimatedTextView> animatedTextViews = new ArrayList<>();

    private int maxRowsTop = 1000000;
    private int maxRowsBottom = 1000000;
    private int maxRowsRegular = 1000000;

    public void clearComments() {
        Log.d(TAG, "clearComments() called, animatedViews=" + animatedTextViews.size());
        animatedTextViews.clear();
        if (binding != null) {
            binding.bulletCommentsContainer.removeAllViews();
        }
    }

    public void setPauseComments(final boolean pause) {
        if (pause) {
            pauseComments();
        } else {
            resumeComments();
        }
    }

    public void pauseComments() {
        animatedTextViews.stream().forEach(s -> s.animator.pause());
    }

    public void resumeComments() {
        animatedTextViews.stream().forEach(s -> s.animator.resume());
    }

    public void drawComments(@NonNull final BulletCommentsInfoItem[] items,
                             final Duration drawUntilPosition) {
        Log.v(TAG, "drawComments() items=" + items.length
                + " position=" + drawUntilPosition.toMillis() + "ms");
        if (binding == null || getWidth() == 0 || getHeight() == 0) {
            Log.w(TAG, "drawComments() skipped: view not ready");
            return;
        }
        if (!layoutSet) {
            setLayout();
            layoutSet = true;
        }
        bulletCommentsInfoItemRegularPool.addAll(
                Arrays.asList(items).stream()
                        .filter(x -> x.getPosition() == BulletCommentsInfoItem.Position.REGULAR)
                        .collect(Collectors.toList()));
        bulletCommentsInfoItemFixedPool.addAll(
                Arrays.asList(items).stream()
                        .filter(x -> x.getPosition() != BulletCommentsInfoItem.Position.REGULAR)
                        .collect(Collectors.toList()));
        final int height = getHeight();
        final int width = getWidth();
        final int calculatedCommentRowsCount =
                height / Math.min(height, width) * commentsRowsCount;
        if (calculatedCommentRowsCount != lastCalculatedCommentsRowsCount) {
            lastCalculatedCommentsRowsCount = calculatedCommentRowsCount;
            rows.clear();
            rowsRegular.clear();
        }
        while (rowsRegular.size() < calculatedCommentRowsCount) {
            rowsRegular.add(new AbstractMap.SimpleEntry<>(0L, 0));
        }
        while (rows.size() < calculatedCommentRowsCount) {
            rows.add(0L);
        }
        drawCommentsByPool(bulletCommentsInfoItemRegularPool, drawUntilPosition,
                height, width, calculatedCommentRowsCount);
        drawCommentsByPool(bulletCommentsInfoItemFixedPool, drawUntilPosition,
                height, width, calculatedCommentRowsCount);
        Log.v(TAG, "drawComments() done, containerChildCount="
                + binding.bulletCommentsContainer.getChildCount());
    }

    public int tryToDrawComment(final BulletCommentsInfoItem item,
                                final int calculatedCommentRowsCount,
                                final int width,
                                final boolean reallyDo) {
        final long current = new Date().getTime();
        int row = -1;
        final int comparedDuration = (int) (commentsDuration * 1000);
        if (item.getPosition().equals(BulletCommentsInfoItem.Position.TOP)
                || item.getPosition().equals(BulletCommentsInfoItem.Position.SUPERCHAT)) {
            for (int i = 0; i < Math.min(maxRowsTop, calculatedCommentRowsCount); i++) {
                final long last = rows.get(i);
                if (current - last >= comparedDuration) {
                    if (reallyDo) {
                        rows.set(i, current);
                    }
                    row = i;
                    break;
                }
            }
        } else if (item.getPosition().equals(BulletCommentsInfoItem.Position.REGULAR)) {
            for (int i = 0; i < Math.min(maxRowsRegular, calculatedCommentRowsCount); i++) {
                final long lastTime = rowsRegular.get(i).getKey();
                final long lastLength = rowsRegular.get(i).getValue();
                final long t = current - lastTime;
                final double tAll = comparedDuration * durationFactor;
                final double lx = (lastLength / 25.0 + 1) * width;
                final double ly = (item.getCommentText().length() / 25.0 + 1) * width;
                final double vx = lx / tAll;
                final double vy = ly / tAll;
                if ((vy - vx) * (tAll - t) < t * vx - (lastLength / 25.0) * width
                        && t * vx - (lastLength / 25.0) * width > 0) {
                    if (reallyDo) {
                        rowsRegular.set(i,
                                new AbstractMap.SimpleEntry<>(current,
                                        item.getCommentText().length()));
                    }
                    row = i;
                    break;
                }
            }
        } else {
            for (int i = calculatedCommentRowsCount - 1;
                 i >= Math.max(0, calculatedCommentRowsCount - maxRowsBottom); i--) {
                final long last = rows.get(i);
                if (current - last >= comparedDuration) {
                    if (reallyDo) {
                        rows.set(i, current);
                    }
                    row = i;
                    break;
                }
            }
        }
        return row;
    }

    private void drawCommentsByPool(final PriorityQueue<BulletCommentsInfoItem> pool,
                                    final Duration drawUntilPosition,
                                    final int height,
                                    final int width,
                                    final int calculatedCommentRowsCount) {
        if (binding == null) {
            return;
        }
        final Context context = binding.bulletCommentsContainer.getContext();
        int drawn = 0;
        while (!pool.isEmpty()
                && (drawUntilPosition.compareTo(Duration.ofSeconds(Long.MAX_VALUE)) == 0
                || pool.peek().getDuration().toMillis() < drawUntilPosition.toMillis())) {
            final BulletCommentsInfoItem item = pool.peek();
            if (item.isLive()
                    && tryToDrawComment(item, calculatedCommentRowsCount, width, false) == -1) {
                Log.v(TAG, "drawCommentsByPool() row collision, skipping item");
                pool.poll(); // skip this item instead of aborting all
                continue;
            }
            pool.poll();
            final TextView textView = new TextView(context);
            final Typeface fontToBeUsed;
            switch (font) {
                case "serif":
                    fontToBeUsed = Typeface.SERIF;
                    break;
                case "monospace":
                    fontToBeUsed = Typeface.MONOSPACE;
                    break;
                case "sans-serif":
                    fontToBeUsed = Typeface.SANS_SERIF;
                    break;
                default:
                    fontToBeUsed = Typeface.DEFAULT;
                    break;
            }
            textView.setGravity(Gravity.CENTER);
            int color = item.getArgbColor();
            if (opacity != 0xFF) {
                color &= 0x00FFFFFF;
                color |= ((opacity & 0xFF) << 24);
            }
            textView.setTextColor(color);
            final String commentText = item.getCommentText();
            Log.v(TAG, "drawCommentsByPool() text=[" + commentText + "] color="
                    + String.format("0x%08X", color) + " pos=" + item.getPosition());
            if (commentText.length() == 0) {
                Log.v(TAG, "drawCommentsByPool() skipping empty text");
                continue;
            }
            textView.setText(commentText);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) (Math.min(height, width) * commentRelativeTextSize
                            * item.getRelativeFontSize()));
            textView.setMaxLines(1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                textView.setTypeface(Typeface.create(fontToBeUsed, Typeface.BOLD,
                        item.getPosition().equals(BulletCommentsInfoItem.Position.SUPERCHAT)));
            } else {
                textView.setTypeface(Typeface.create(fontToBeUsed, Typeface.BOLD));
            }
            final Paint paint = textView.getPaint();
            int shadowColor = Color.BLACK & 0x00FFFFFF;
            shadowColor |= ((opacity & 0xFF) << 24);
            paint.setShadowLayer(outlineRadius, 0, 0, shadowColor);
            textView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint);

            final int row = tryToDrawComment(item, calculatedCommentRowsCount, width, true);
            if (row == -1) {
                continue;
            }
            textView.setX(width);
            textView.post(() -> {
                final int textWidth = textView.getWidth();
                final int textHeight = textView.getHeight();
                final ObjectAnimator animator;
                if (!item.getPosition().equals(BulletCommentsInfoItem.Position.REGULAR)) {
                    animator = ObjectAnimator.ofFloat(
                            textView,
                            View.TRANSLATION_X,
                            (float) ((width - textWidth) / 2.0),
                            (float) ((width - textWidth) / 2.0)
                    );
                } else {
                    animator = ObjectAnimator.ofFloat(
                            textView,
                            View.TRANSLATION_X,
                            width,
                            -textWidth
                    );
                }
                textView.setY((float) (height * (0.5 + row) / calculatedCommentRowsCount
                        - textHeight / 2));

                final AnimatedTextView animatedTextView = new AnimatedTextView(
                        textView, animator);
                animatedTextViews.add(animatedTextView);
                animator.setFrameDelay(1);
                animator.setInterpolator(new LinearInterpolator());
                animator.setDuration(item.getLastingTime() != -1
                        ? item.getLastingTime()
                        : (long) (commentsDuration * 1000
                        * (item.getPosition().equals(BulletCommentsInfoItem.Position.REGULAR)
                        ? durationFactor : 1)));
                animator.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(final Animator animation) {
                        binding.bulletCommentsContainer.removeView(textView);
                        animatedTextViews.remove(animatedTextView);
                    }
                });
                animator.start();
            });
            binding.bulletCommentsContainer.addView(textView);
            drawn++;
        }
        if (drawn > 0) {
            Log.v(TAG, "drawCommentsByPool() drawn=" + drawn);
        }
    }
}
