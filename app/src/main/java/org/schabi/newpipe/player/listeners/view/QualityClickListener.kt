package org.schabi.newpipe.player.listeners.view

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import androidx.appcompat.widget.PopupMenu
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.player.Player

/**
 * Click listener for the qualityTextView of the player
 */
class QualityClickListener(
    private val player: Player,
    private val qualityPopupMenu: PopupMenu
) : View.OnClickListener {

    companion object {
        private const val TAG: String = "QualityClickListener"
    }

    @SuppressLint("SetTextI18n") // we don't need I18N because of a " "
    override fun onClick(v: View) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "onQualitySelectorClicked() called")
        }

        qualityPopupMenu.show()
        player.isSomePopupMenuVisible = true

        val videoStream = player.selectedVideoStream
        if (videoStream != null) {
            player.binding.qualityTextView.text =
                MediaFormat.getNameById(videoStream.formatId) + " " + videoStream.resolution
        }

        player.saveWasPlaying()
        player.manageControlsAfterOnClick(v)
    }
}
