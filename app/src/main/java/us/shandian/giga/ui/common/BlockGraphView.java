package us.shandian.giga.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import org.schabi.newpipe.R;
import us.shandian.giga.get.DownloadMission;

public class BlockGraphView extends View
{
	private static int BLOCKS_PER_LINE = 15;
	
	private int mForeground, mBackground;
	private int mBlockSize, mLineCount;
	private DownloadMission mMission;
	
	public BlockGraphView(Context context) {
		this(context, null);
	}
	
	public BlockGraphView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public BlockGraphView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		try {
			TypedArray array = context.obtainStyledAttributes(R.styleable.AppCompatTheme);
			mBackground = array.getColor(R.styleable.AppCompatTheme_colorPrimary, 0);
			mForeground = array.getColor(R.styleable.AppCompatTheme_colorPrimaryDark, 0);
			array.recycle();
		} catch (Exception e) {
			
		}
	}
	
	public void setMission(DownloadMission mission) {
		mMission = mission;
		setWillNotDraw(false);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = MeasureSpec.getSize(widthMeasureSpec);
		mBlockSize = width / BLOCKS_PER_LINE - 1;
		mLineCount = (int) Math.ceil((double) mMission.blocks / BLOCKS_PER_LINE);
		int height = mLineCount * (mBlockSize + 1);
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		Paint p = new Paint();
		p.setFlags(Paint.ANTI_ALIAS_FLAG);
		
		for (int i = 0; i < mLineCount; i++) {
			for (int j = 0; j < BLOCKS_PER_LINE; j++) {
				long pos = i * BLOCKS_PER_LINE + j;
				if (pos >= mMission.blocks) {
					break;
				}
				
				if (mMission.isBlockPreserved(pos)) {
					p.setColor(mForeground);
				} else {
					p.setColor(mBackground);
				}
				
				int left = (mBlockSize + 1) * j;
				int right = left + mBlockSize;
				int top = (mBlockSize + 1) * i;
				int bottom = top + mBlockSize;
				canvas.drawRect(left, top, right, bottom, p);
			}
		}
	}
}
