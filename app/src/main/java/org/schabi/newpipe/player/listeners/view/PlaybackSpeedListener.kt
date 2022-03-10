package org.schabi.newpipe.player.listeners.view

import android.util.Log
import android.view.View
import android.widget.PopupMenu
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlaybackParameterDialog

class PlaybackSpeedListener(
    private val player: Player,
    private val playbackSpeedPopupMenu: PopupMenu

) : View.OnClickListener {

    companion object {
        private val DEBUG = MainActivity.DEBUG
        private val TAG: String = PlaybackSpeedListener::class.java.simpleName
    }

    override fun onClick(v: View) {
        if (player.binding.qualityTextView.id == v.id) {
            if (DEBUG) {
                Log.d(TAG, "onPlaybackSpeedClicked() called")
            }

            if (player.videoPlayerSelected()) {
                PlaybackParameterDialog.newInstance(
                    player.playbackSpeed.toDouble(),
                    player.playbackPitch.toDouble(),
                    player.playbackSkipSilence
                ) { speed: Float, pitch: Float, skipSilence: Boolean ->
                    player.setPlaybackParameters(
                        speed,
                        pitch,
                        skipSilence
                    )
                }
                    .show(player.parentActivity!!.supportFragmentManager, null)
            } else {
                playbackSpeedPopupMenu.show()
                player.isSomePopupMenuVisible = true
            }
        }
    }
}
