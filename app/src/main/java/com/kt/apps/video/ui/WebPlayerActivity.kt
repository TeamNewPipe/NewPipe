package com.kt.apps.video.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView.setWebContentsDebuggingEnabled
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kt.apps.video.ITubeIntegration
import com.kt.apps.video.utils.isPipSettingAllowed
import com.kt.apps.video.viewmodel.data.Event
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.YoutubePage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.getSearchQuery
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.getVideoId
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.isYouTubePlay
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.isYouTubeSearch
import kotlinx.coroutines.launch
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.databinding.LayoutWebPlayerBinding
import org.schabi.newpipe.player.helper.PlayerHelper
import timber.log.Timber

class WebPlayerActivity : AppCompatActivity() {
    private var onUserLeaveHintCallback: (() -> Unit)? = null
    private var onNewIntentCallback: (() -> Unit)? = null
    private lateinit var binding: LayoutWebPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutWebPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initYouTubePlayerView()

        if (!loadVideoFromIntent(intent)) {
            binding.youtubePlayerView.openYoutubePage(YoutubePage.Home)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerEnterPictureInPictureMode()
        }

        lifecycleScope.launch {
            ITubeIntegration.instance.commonRepository.registerCommonEvents().collect {
                if (it == Event.HideVideoDetail.Web) {
                    finishAndRemoveTask()
                }
            }
        }
    }

    private fun loadVideoFromIntent(intent: Intent?): Boolean {
        val youtubePlayerView = binding.youtubePlayerView
        intent?.data?.run {
            when {
                isYouTubePlay() -> {
                    val vid = getVideoId() ?: return false
                    youtubePlayerView.getYouTubePlayerWhenReady(object :
                            YouTubePlayerCallback {
                            override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                                Timber.d("loadVideo: $vid")
                                youTubePlayer.loadVideo(vid, 0f)
                            }
                        })
                    return true
                }

                isYouTubeSearch() -> {
                    youtubePlayerView.openYoutubePage(
                        YoutubePage.Search(
                            searchQuery = getSearchQuery() ?: ""
                        )
                    )
                    return true
                }

                else -> {}
            }
        }
        return false
    }

    private fun initYouTubePlayerView() {
        setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        binding.youtubePlayerView.run {
            val iFramePlayerOptions = IFramePlayerOptions.Builder()
                .controls(1)
                .autoplay(1)
                .rel(1)
                .fullscreen(0)
                .autoPlayNextVideo(true)
                .mediaSession(true)
                .build()
            enableAutomaticInitialization = false
            initialize(
                object : AbstractYouTubePlayerListener() {
                    @SuppressLint("SetTextI18n")
                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError
                    ) {
                        super.onError(youTubePlayer, error)
                        binding.textErrDesc.text =
                            "Không thể phát video. Vui lòng thử lại sau (${error.name})"
                    }

                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        super.onReady(youTubePlayer)
                        visibility = View.VISIBLE
                    }
                },
                iFramePlayerOptions
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onNewIntentCallback?.invoke()
        if (intent?.action == "request_enter_picture_in_picture") {
            @SuppressLint("NewApi")
            val result = requestEnterPictureInPictureMode()
            if (result && !binding.youtubePlayerView.isPlaying()) {
                binding.youtubePlayerView.getYouTubePlayerWhenReady(object : YouTubePlayerCallback {
                    override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.play()
                    }
                })
            }
        } else {
            loadVideoFromIntent(intent)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Timber.tag("YouTubePlayer").d("onUserLeaveHint")
        onUserLeaveHintCallback?.invoke()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerEnterPictureInPictureMode() {
        // ////////////////////
        // Check back pressed
        // ////////////////////
        var isPlaying = binding.youtubePlayerView.isPlaying()
        var isInPictureInPictureMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else {
            false
        }

        // Handle callback.enabled by listening pip mode and youtube playing state
        val onBackPressCallback = object : OnBackPressedCallback(isPlaying && !isInPictureInPictureMode) {
            override fun handleOnBackPressed() {
                if (!requestEnterPictureInPictureMode()) {
                    isEnabled = false
                    moveTaskToBack(true)
                }
            }
        }
        val checkOnBackPressState = {
            onBackPressCallback.isEnabled = isPlaying && !isInPictureInPictureMode
        }

        binding.youtubePlayerView.addYouTubePlayerListener(object :
                AbstractYouTubePlayerListener() {
                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    isPlaying = state == PlayerConstants.PlayerState.PLAYING
                    checkOnBackPressState()
                }
            })
        addOnPictureInPictureModeChangedListener {
            isInPictureInPictureMode = it.isInPictureInPictureMode
            checkOnBackPressState()
        }
        onBackPressedDispatcher.addCallback(this, onBackPressCallback)

        // ////////////////////
        // Check leave hint
        // ////////////////////
        onUserLeaveHintCallback = {
            if (isPipSettingAllowed() && onBackPressCallback.isEnabled) {
                requestEnterPictureInPictureMode()
            }
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun requestEnterPictureInPictureMode(): Boolean {
        Timber.tag("YouTubePlayer").d("requestEnterPictureInPictureMode")
        return when {
            isPipSettingAllowed() -> {
                enterPictureInPictureMode()
                true
            }

            else -> {
                false
            }
        }
    }

    private fun isPipSettingAllowed(): Boolean {
        return isPipSettingAllowed(this) && PlayerHelper.getMinimizeOnExitAction(this) != PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("YouTubePlayer").d("onDestroy")
        binding.youtubePlayerView.release()
    }
}
