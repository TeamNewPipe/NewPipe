package org.schabi.newpipe.views;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ProgressBar;

public final class AnimatedProgressBar extends ProgressBar {

	@Nullable
	private ProgressBarAnimation animation = null;

	public AnimatedProgressBar(Context context) {
		super(context);
	}

	public AnimatedProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AnimatedProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public synchronized void setProgressAnimated(int progress) {
		cancelAnimation();
		animation = new ProgressBarAnimation(this, getProgress(), progress);
		startAnimation(animation);
	}

	private void cancelAnimation() {
		if (animation != null) {
			animation.cancel();
			animation = null;
		}
		clearAnimation();
	}

	private static class ProgressBarAnimation extends Animation {

		private final AnimatedProgressBar progressBar;
		private final float from;
		private final float to;

		ProgressBarAnimation(AnimatedProgressBar progressBar, float from, float to) {
			super();
			this.progressBar = progressBar;
			this.from = from;
			this.to = to;
			setDuration(500);
			setInterpolator(new AccelerateDecelerateInterpolator());
		}

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			super.applyTransformation(interpolatedTime, t);
			float value = from + (to - from) * interpolatedTime;
			progressBar.setProgress((int) value);
		}
	}
}
