package org.schabi.newpipe.views.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.END
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.START
import androidx.constraintlayout.widget.ConstraintSet
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.event.DisplayPortion
import org.schabi.newpipe.player.event.DoubleTapListener

class PlayerFastSeekOverlay(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs), DoubleTapListener {

    private var secondsView: SecondsView
    private var circleClipTapView: CircleClipTapView
    private var rootConstraintLayout: ConstraintLayout

    private var wasForwarding: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.player_fast_seek_overlay, this, true)

        secondsView = findViewById(R.id.seconds_view)
        circleClipTapView = findViewById(R.id.circle_clip_tap_view)
        rootConstraintLayout = findViewById(R.id.root_constraint_layout)

        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            circleClipTapView.updateArcSize(view)
        }
    }

    private var performListener: PerformListener? = null

    fun performListener(listener: PerformListener) = apply {
        performListener = listener
    }

    var seekSeconds: Int = 0
        private set

    fun seekSeconds(seconds: Int) = apply {
        seekSeconds = seconds
    }

    // Indicates whether this (double) tap is the first of a series
    // Decides whether to call performListener.onAnimationStart or not
    private var initTap: Boolean = false

    override fun onDoubleTapStarted(portion: DisplayPortion) {
        if (DEBUG)
            Log.d(TAG, "onDoubleTapStarted called with portion = [$portion]")

        initTap = false

        secondsView.stopAnimation()
    }

    override fun onDoubleTapProgressDown(portion: DisplayPortion) {
        val shouldForward: Boolean = performListener?.shouldFastForward(portion) ?: return

        if (DEBUG)
            Log.d(
                TAG,
                "onDoubleTapProgressDown called with " +
                    "shouldForward = [$shouldForward], " +
                    "wasForwarding = [$wasForwarding], " +
                    "initTap = [$initTap], "
            )

        /*
         * Check if a initial tab occurred or if direction was switched
         */
        if (!initTap || wasForwarding != shouldForward) {
            // Reset seconds and update position
            secondsView.seconds = 0
            changeConstraints(shouldForward)
            circleClipTapView.updatePosition(!shouldForward)
            secondsView.setForwarding(shouldForward)

            wasForwarding = shouldForward

            if (!initTap) {
                initTap = true
            }
        }

        performListener?.onDoubleTab()

        secondsView.seconds += seekSeconds
        performListener?.seek(forward = shouldForward)
    }

    override fun onDoubleTapFinished() {
        if (DEBUG)
            Log.d(TAG, "onDoubleTapFinished called with initTap = [$initTap]")

        if (initTap) performListener?.onDoubleTabEnd()
        initTap = false

        secondsView.stopAnimation()
    }

    private fun changeConstraints(forward: Boolean) {
        val constraintSet = ConstraintSet()
        with(constraintSet) {
            clone(rootConstraintLayout)
            clear(secondsView.id, if (forward) START else END)
            connect(
                secondsView.id, if (forward) END else START,
                PARENT_ID, if (forward) END else START
            )
            secondsView.startAnimation()
            applyTo(rootConstraintLayout)
        }
    }

    interface PerformListener {
        fun onDoubleTab()
        fun onDoubleTabEnd()
        fun shouldFastForward(portion: DisplayPortion): Boolean?
        fun seek(forward: Boolean)
    }

    companion object {
        private const val TAG = "PlayerSeekOverlay"
        private val DEBUG = MainActivity.DEBUG
    }
}
