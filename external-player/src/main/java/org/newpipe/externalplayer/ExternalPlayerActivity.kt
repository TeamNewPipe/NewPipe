package org.newpipe.externalplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.newpipe.externalplayer.databinding.ActivityExternalPlayerBinding

class ExternalPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalPlayerBinding
    private var serviceStarted = false
    private val speeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f)
    private var speedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExternalPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIncomingIntent(intent)

        binding.enterPipButton.setOnClickListener { enterPip() }
        binding.speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val speed = speeds[speedIndex]
            binding.speedButton.text = "${speed}x"
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PLAY
                putExtra("speed", speed)
            }
            ContextCompat.startForegroundService(this, intent)
        }
        binding.subToggle.setOnClickListener {
            val newText = if (binding.subToggle.text == "SUB") "SUB:OFF" else "SUB"
            binding.subToggle.text = newText
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                return super.onScroll(e1, e2, distanceX, distanceY)
            }
        })

        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (Intent.ACTION_VIEW == action || data != null) {
            data?.let { uri ->
                binding.urlText.text = uri.toString()
                startServiceWithUri(uri.toString())
            }
        } else if (Intent.ACTION_SEND == action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_STREAM)?.toString()
            text?.let {
                val url = extractUrlFromText(it)
                if (url != null) startServiceWithUri(url)
            }
        }
    }

    private fun startServiceWithUri(uri: String) {
        val intent = Intent(this, PlayerService::class.java).apply {
            action = PlayerService.ACTION_SET_URI
            putExtra(PlayerService.EXTRA_URI, uri)
        }
        ContextCompat.startForegroundService(this, intent)
        serviceStarted = true
    }

    private fun extractUrlFromText(text: String): String? {
        val regex = "(https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=%]+)".toRegex()
        return regex.find(text)?.value
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
}