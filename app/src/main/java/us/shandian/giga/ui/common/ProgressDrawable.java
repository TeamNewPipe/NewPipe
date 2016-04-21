package us.shandian.giga.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class ProgressDrawable extends Drawable
{
	private float mProgress = 0.0f;
	private int mBackgroundColor, mForegroundColor;
	
	public ProgressDrawable(Context context, int background, int foreground) {
		this(context.getResources().getColor(background), context.getResources().getColor(foreground));
	}
	
	public ProgressDrawable(int background, int foreground) {
		mBackgroundColor = background;
		mForegroundColor = foreground;
	}
	
	public void setProgress(float progress) {
		mProgress = progress;
		invalidateSelf();
	}

	@Override
	public void draw(Canvas canvas) {
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		
		Paint paint = new Paint();
		
		paint.setColor(mBackgroundColor);
		canvas.drawRect(0, 0, width, height, paint);
		
		paint.setColor(mForegroundColor);
		canvas.drawRect(0, 0, (int) (mProgress * width), height, paint);
	}

	@Override
	public void setAlpha(int alpha) {
		// Unsupported
	}

	@Override
	public void setColorFilter(ColorFilter filter) {
		// Unsupported
	}

	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}

}
