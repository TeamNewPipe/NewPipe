package org.schabi.newpipe.player.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding

/** Reuses the bound controls, so playback actions and accessibility labels stay identical. */
class BottomPlayerControls(private val binding: PlayerBinding) {
    private val context = binding.root.context
    private val narrowMetadata = NarrowPlayerMetadata(binding)
    private var panel: LinearLayout? = null
    private var positions = emptyList<Position>()
    private var buttonStates = emptyList<ButtonState>()
    private var metadataParams: ViewGroup.LayoutParams? = null
    private var moreVisibility = View.VISIBLE
    private var secondaryVisibility = View.INVISIBLE
    private var seekUp = View.NO_ID
    private var seekDown = View.NO_ID
    private var focusStates = emptyList<FocusState>()

    fun applyWindowInsets(fullscreen: Boolean, controlsPadding: Int, topControlsPadding: Int) {
        val insets = ViewCompat.getRootWindowInsets(binding.root)
        val bars = insets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
        val cutout = insets?.getInsets(WindowInsetsCompat.Type.displayCutout()) ?: Insets.NONE
        applyInsets(
            fullscreen,
            VideoPlayerUi.calculateControlsEdgePadding(fullscreen, controlsPadding, bars.left, cutout.left),
            VideoPlayerUi.calculateTopControlsPadding(fullscreen, topControlsPadding, bars.top, cutout.top),
            VideoPlayerUi.calculateControlsEdgePadding(fullscreen, controlsPadding, bars.right, cutout.right),
            VideoPlayerUi.calculateControlsEdgePadding(fullscreen, 0, bars.bottom, cutout.bottom)
        )
    }

    fun applyInsets(fullscreen: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (update(fullscreen, left, top, right, bottom)) {
            binding.topControls.setPadding(0, 0, 0, 0)
        } else {
            binding.topControls.setPadding(left, top, right, 0)
            binding.bottomControls.setPadding(left, 0, right, bottom)
        }
    }

    fun update(fullscreen: Boolean, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val wanted = fullscreen && PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.bottom_player_controls_key), false)
        if (wanted) narrowMetadata.restore()
        if (wanted && panel == null) attach()
        if (!wanted && panel != null) restore()
        val active = panel ?: run {
            narrowMetadata.update(fullscreen)
            return false
        }
        active.setPadding(maxOf(left, dp(16)), dp(12), maxOf(right, dp(16)), bottom + dp(8))
        // Keep the whole panel below a cutout even on a short landscape screen.
        val params = active.layoutParams as RelativeLayout.LayoutParams
        if (params.topMargin != top) {
            params.topMargin = top
            active.layoutParams = params
        }
        binding.moreOptionsButton.visibility = View.GONE
        settleSecondaryAnimation()
        binding.secondaryControls.visibility = View.VISIBLE
        binding.secondaryControls.alpha = 1f
        binding.bottomControls.setPadding(0, 0, 0, 0)
        val primaryParams = binding.primaryControls.layoutParams
        if (primaryParams.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            primaryParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.primaryControls.layoutParams = primaryParams
        }
        return true
    }

    private fun attach() {
        val focused = binding.root.findFocus()
        val transport = binding.playPauseButton.parent as LinearLayout
        val moved = listOf(
            binding.metadataView,
            transport,
            binding.bottomSeekbarPreviewLayout,
            binding.bottomControls,
            binding.primaryControls,
            binding.secondaryControls,
            binding.sleepTimerCountdown
        )
        positions = moved.map { view ->
            val parent = view.parent as ViewGroup
            Position(view, parent, parent.indexOfChild(view), view.layoutParams)
        }
        buttonStates = listOf(binding.playPreviousButton, binding.playPauseButton, binding.playNextButton).map {
            ButtonState(it, it.layoutParams, it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom, it.nextFocusUpId, it.nextFocusDownId)
        }
        metadataParams = binding.metadataControls.layoutParams
        moreVisibility = binding.moreOptionsButton.visibility
        secondaryVisibility = binding.secondaryControls.visibility
        // A closing More animation leaves a negative translation, and its end callback can
        // hide the row after it has moved. The bottom panel owns a settled, visible row.
        settleSecondaryAnimation()
        seekUp = binding.playbackSeekBar.nextFocusUpId
        seekDown = binding.playbackSeekBar.nextFocusDownId
        focusStates = (descendants(binding.primaryControls) + descendants(binding.secondaryControls) + binding.screenRotationButton).map {
            FocusState(it, it.nextFocusUpId, it.nextFocusDownId)
        }
        descendants(binding.primaryControls).forEach { it.nextFocusDownId = R.id.playbackSeekBar }
        descendants(binding.secondaryControls).forEach { it.nextFocusUpId = R.id.playbackSeekBar }
        moved.forEach { (it.parent as ViewGroup).removeView(it) }

        val content = LinearLayout(context).apply {
            id = R.id.bottom_player_controls_panel
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.player_controls_background)
            isClickable = false
        }
        panel = content
        binding.playbackWindowRoot.addView(
            content,
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        )
        content.addView(binding.metadataView, rowParams())
        binding.titleTextView.setPresentationScale(1.2f)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val actions = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
        }
        actions.addView(actionRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(actions, rowParams())
        // Remove weighted spacing from the centered transport controls in this layout only.
        actionRow.addView(transport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        buttonStates.forEach {
            it.view.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            it.view.setPadding(dp(12), dp(12), dp(12), dp(12))
            it.view.nextFocusUpId = View.NO_ID
            it.view.nextFocusDownId = R.id.playbackSeekBar
        }
        actionRow.addView(binding.primaryControls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        binding.metadataControls.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48))
        content.addView(binding.bottomSeekbarPreviewLayout, rowParams())
        content.addView(binding.bottomControls, rowParams())
        content.addView(binding.secondaryControls, rowParams())
        content.addView(binding.sleepTimerCountdown, rowParams())
        binding.playbackSeekBar.nextFocusUpId = R.id.playPauseButton
        // Left/right on the seek bar seeks, so fullscreen needs a vertical D-pad route.
        binding.playbackSeekBar.nextFocusDownId = R.id.screenRotationButton
        binding.screenRotationButton.nextFocusDownId = R.id.resizeTextView
        binding.resizeTextView.nextFocusUpId = R.id.screenRotationButton
        focused?.requestFocus()
    }

    fun restore() {
        narrowMetadata.restore()
        val active = panel ?: return
        val focused = binding.root.findFocus()
        positions.forEach { position ->
            (position.view.parent as ViewGroup).removeView(position.view)
            position.parent.addView(position.view, position.index.coerceAtMost(position.parent.childCount), position.params)
        }
        buttonStates.forEach {
            it.view.layoutParams = it.params
            it.view.setPadding(it.left, it.top, it.right, it.bottom)
            it.view.nextFocusUpId = it.up
            it.view.nextFocusDownId = it.down
        }
        binding.metadataControls.layoutParams = metadataParams
        binding.titleTextView.setPresentationScale(1.0f)
        binding.moreOptionsButton.visibility = moreVisibility
        settleSecondaryAnimation()
        binding.secondaryControls.visibility = secondaryVisibility
        binding.secondaryControls.alpha = if (secondaryVisibility == View.VISIBLE) 1f else 0f
        // An interrupted transition is restored in its visible state, never half faded.
        binding.moreOptionsButton.animate().setListener(null).cancel()
        binding.moreOptionsButton.rotation = if (secondaryVisibility == View.VISIBLE) 180f else 0f
        binding.playbackSeekBar.nextFocusUpId = seekUp
        binding.playbackSeekBar.nextFocusDownId = seekDown
        focusStates.forEach {
            it.view.nextFocusUpId = it.up
            it.view.nextFocusDownId = it.down
        }
        (active.parent as ViewGroup).removeView(active)
        panel = null
        positions = emptyList()
        buttonStates = emptyList()
        focusStates = emptyList()
        focused?.requestFocus()
    }

    private fun settleSecondaryAnimation() {
        binding.secondaryControls.apply {
            // Remove the listener first: cancel also dispatches onAnimationEnd on Android.
            animate().setListener(null).cancel()
            clearAnimation()
            translationY = 0f
        }
    }

    private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private fun descendants(group: ViewGroup): List<View> = group.children.flatMap {
        sequenceOf(it) + if (it is ViewGroup) descendants(it).asSequence() else emptySequence()
    }.toList()

    private data class Position(val view: View, val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
    private data class FocusState(val view: View, val up: Int, val down: Int)
    private data class ButtonState(
        val view: View,
        val params: ViewGroup.LayoutParams,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val up: Int,
        val down: Int
    )
}
