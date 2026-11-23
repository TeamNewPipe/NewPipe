package org.schabi.newpipe.player.ui

import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player

internal object SubtitleTimingDialog {
    fun show(anchor: View, player: Player) {
        val builder = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(anchor))
        val value = TextView(builder.context).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun update(delta: Long) {
            val timing = player.subtitleTiming
            timing.setOffset(timing.offsetMs + delta)
            value.text = anchor.context.getString(R.string.subtitle_timing_value, timing.offsetMs)
            if (!player.exoPlayerIsNull()) {
                // Re-read cues at the current position, including when playback is paused.
                player.exoPlayer.seekTo(player.exoPlayer.currentPosition)
                player.triggerProgressUpdate()
            }
        }
        value.text = anchor.context.getString(R.string.subtitle_timing_value, player.subtitleTiming.offsetMs)
        val dialog = builder.setTitle(R.string.subtitle_timing)
            .setView(value)
            .setNegativeButton(R.string.subtitle_earlier, null)
            .setPositiveButton(R.string.subtitle_later, null)
            .setNeutralButton(R.string.subtitle_timing_reset, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-2).setOnClickListener { update(-100) }
            dialog.getButton(-1).setOnClickListener { update(100) }
            dialog.getButton(-3).setOnClickListener { update(-player.subtitleTiming.offsetMs) }
        }
        AnchoredPlaybackDialog.show(anchor, dialog)
    }
}
