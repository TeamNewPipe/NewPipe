@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.playback

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.common.Player
import androidx.media3.exoplayer.video.PlaceholderSurface

/**
 * Prevent error message: 'Unrecoverable player error occurred'
 * In case of rotation some users see this kind of an error which is preventable
 * having a Callback that handles the lifecycle of the surface.
 * <p>
 * How?: In case we are no longer able to write to the surface eg. through rotation/putting in
 * background we set set a DummySurface. Although it it works on API >= 23 only.
 * Result: we get a little video interruption (audio is still fine) but we won't get the
 * 'Unrecoverable player error occurred' error message.
 * <p>
 * This implementation is based on:
 * 'ExoPlayer stuck in buffering after re-adding the surface view a few time #2703'
 * <p>
 * -> exoplayer fix suggestion link
 * https://github.com/google/ExoPlayer/issues/2703#issuecomment-300599981
 */
class SurfaceHolderCallback(
    private val context: Context,
    private val player: Player
) : SurfaceHolder.Callback {

    private var placeholderSurface: PlaceholderSurface? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        player.setVideoSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (placeholderSurface == null) {
            placeholderSurface = PlaceholderSurface.newInstanceV17(context, false)
        }
        player.setVideoSurface(placeholderSurface)
    }

    fun release() {
        placeholderSurface?.let {
            it.release()
            placeholderSurface = null
        }
    }
}
