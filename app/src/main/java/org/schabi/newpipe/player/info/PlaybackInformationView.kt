package org.schabi.newpipe.player.info

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.schabi.newpipe.R

/** A window-local panel: no popup window, Activity retention, or background polling. */
class PlaybackInformationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val details = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
    }
    private var information: PlaybackInformation? = null
    private val refresh = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE) return
            details.text = information?.text(context).orEmpty()
            postDelayed(this, 1000)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(0xE6222222.toInt())
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, padding)
        val header = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(
            TextView(context).apply {
                setText(R.string.playback_information)
                setTextColor(Color.WHITE)
                textSize = 14f
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            ImageButton(context).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = context.getString(R.string.playback_info_close)
                setOnClickListener { this@PlaybackInformationView.visibility = GONE }
            },
            LayoutParams(padding * 6, padding * 6)
        )
        addView(header)
        addView(ScrollView(context).apply { addView(details) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun bind(source: PlaybackInformation) {
        information = source
        restart()
    }

    fun toggle() {
        visibility = if (visibility == VISIBLE) GONE else VISIBLE
    }

    fun clear() {
        visibility = GONE
        removeCallbacks(refresh)
        information = null
    }

    private fun restart() {
        removeCallbacks(refresh)
        // Visibility callbacks may run before ancestor/window visibility has settled.
        // Check visibility in the posted refresh, after that transition completes.
        if (isAttachedToWindow) post(refresh)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View constructors can dispatch this before Kotlin fields are initialized.
        if (information != null) restart()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (information != null) restart()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (handled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Claim the gesture before its first move can drag the player sheet or app bar.
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (!handled || event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximum = (320 * resources.displayMetrics.density).toInt()
        // The incoming bound already accounts for margins and the player's current size.
        // A parent's laid-out height may still describe the previous orientation.
        val boundedHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> heightMeasureSpec
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(minOf(maximum, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, boundedHeight)
    }
}
