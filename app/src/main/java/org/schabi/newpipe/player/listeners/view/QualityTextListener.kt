package org.schabi.newpipe.player.listeners.view

import android.util.Log
import android.view.View
import android.widget.PopupMenu
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.player.Player

class QualityTextListener(
    private val player: Player,
    private val qualityPopupMenu: PopupMenu
) : View.OnClickListener {

    companion object {
        private val DEBUG = MainActivity.DEBUG
        private val TAG: String = QualityTextListener::class.java.simpleName
    }

    override fun onClick(v: View) {
        if (player.binding.qualityTextView.id == v.id) {
            if (DEBUG) {
                Log.d(TAG, "onQualitySelectorClicked() called")
            }

            qualityPopupMenu.show()
            player.isSomePopupMenuVisible = true

            val videoStream = player.selectedVideoStream
            if (videoStream != null) {
                val qualityText = (
                    MediaFormat.getNameById(videoStream.formatId) + " " +
                        videoStream.resolution
                    )
                player.binding.qualityTextView.text = qualityText
            }

            player.saveWasPlaying()
        }
    }
}
