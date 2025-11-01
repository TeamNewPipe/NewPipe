package org.newpipe.externalplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.STATE_READY
import com.google.android.exoplayer2.SimpleExoPlayer
import org.newpipe.externalplayer.databinding.ActivityExternalPlayerBinding

class ExternalPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalPlayerBinding
    private var player: SimpleExoPlayer? = null
    private var playWhenReady = true
    private var playbackPosition: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExternalPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIncomingIntent(intent)

        binding.enterPipButton.setOnClickListener {
            enterPip()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (Intent.ACTION_VIEW == action || data != null) {
            data?.let { uri ->
                binding.urlText.text = uri.toString()
                initializePlayer(uri.toString())
            }
        } else if (Intent.ACTION_SEND == action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.toString()
            text?.let {
                val url = extractUrlFromText(it)
                if (url != null) initializePlayer(url)
            }
        }
    }

    private fun extractUrlFromText(text: String): String? {
        val regex = "(https?://[\w\-._~:/?#[\]@!$&'()*+,;=%]+)".toRegex()
        return regex.find(text)?.value
    }

    private fun initializePlayer(url: String) {
        if (player == null) {
            player = SimpleExoPlayer.Builder(this).build()
            binding.playerView.player = player
        }
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        player!!.setMediaItem(mediaItem)
        player!!.playWhenReady = playWhenReady
        player!!.seekTo(playbackPosition)
        player!!.prepare()
        player!!.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                binding.loadingView.visibility = if (state == STATE_READY) View.GONE else View.VISIBLE
            }
        })
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ratio = Rational(binding.playerView.width.takeIf { it > 0 } ?: 16, binding.playerView.height.takeIf { it > 0 } ?: 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPip()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let {
            playbackPosition = it.currentPosition
            playWhenReady = it.playWhenReady
            it.playWhenReady = false
        }
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = playWhenReady
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let {
            playbackPosition = it.currentPosition
            playWhenReady = it.playWhenReady
            it.release()
            player = null
        }
    }
}