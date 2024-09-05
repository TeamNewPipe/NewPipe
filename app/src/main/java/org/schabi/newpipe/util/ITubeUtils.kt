@file:JvmName("ITubeUtils")

package org.schabi.newpipe.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.PaintDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.kt.apps.video.data.LogEvent
import com.kt.apps.video.data.OpenVideoDetailData
import com.kt.apps.video.data.StreamSourceArea
import com.kt.apps.video.data.logger
import com.kt.apps.video.domain.CheckNewVersion
import com.kt.apps.video.ui.popup.showDialogUpdateRequired
import com.kt.apps.video.viewmodel.ITubeAppViewModel
import com.kt.apps.video.viewmodel.data.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.views.NewPipeTextView
import timber.log.Timber

fun Int.dpToPx(): Int {
    return toFloat().dpToPx()
}

fun Float.dpToPx(): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        App.getApp().resources.getDisplayMetrics()
    ).toInt()
}

fun MainActivity.observe(viewModel: ITubeAppViewModel) {
    lifecycleScope.launch {
        viewModel.event.collect {
            when (it) {
                is Event.ShowUpdateRequired -> {
                    showDialogUpdateRequired(it.title, it.subtitle, it.action) {
                        viewModel.onTapDialogPositiveAction(it)
                    }
                }
                else -> {}
            }
        }
    }
}

fun <T> Fragment.collect(flow: Flow<T>, action: (value: T) -> Unit) {
    lifecycleScope.launch {
        flow.collect(action)
    }
}

fun <T> ComponentActivity.collect(flow: Flow<T>, action: (value: T) -> Unit) {
    lifecycleScope.launch {
        flow.collect(action)
    }
}

fun Context.createNewVersionHeader(
    newVersion: CheckNewVersion.HintNewVersion,
    onCLickCloseAction: () -> Unit,
    onClickAction: () -> Unit
): View {
    return ConstraintLayout(this).also {
        it.setPadding(24f.dpToPx(), 8f.dpToPx(), 24f.dpToPx(), 8f.dpToPx())
        it.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        View(this).apply {
            val contentBackground = PaintDrawable(0x33FFFFFF)
            contentBackground.setCornerRadius(4f.dpToPx().toFloat())
            background = contentBackground
            val params = ConstraintLayout.LayoutParams(0, 0)
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            it.addView(this, params)
        }
        val actionViewId = View.generateViewId()
        val closeViewId = View.generateViewId()
        val textView = NewPipeTextView(this@createNewVersionHeader).apply {
            id = View.generateViewId()
            text = newVersion.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            val params = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToStart = actionViewId
            params.topMargin = 16f.dpToPx()
            params.marginStart = 24.dpToPx()
            it.addView(this, params)
        }
        val subTextView = NewPipeTextView(this@createNewVersionHeader).apply {
            id = View.generateViewId()
            text = newVersion.subtitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val params = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.startToStart = textView.id
            params.endToEnd = textView.id
            params.topToBottom = textView.id
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomMargin = 16.dpToPx()
            it.addView(this, params)
        }
        val actionView = NewPipeTextView(this@createNewVersionHeader).apply {
            id = actionViewId
            text = newVersion.action
            setTextColor(Color.WHITE)
            val actionBackground = PaintDrawable(0x99FFFFFF.toInt())
            actionBackground.setCornerRadius(48f.dpToPx().toFloat())
            actionBackground.paint.style = Paint.Style.STROKE
            actionBackground.paint.strokeWidth = 1.5f.dpToPx().toFloat()
            background = actionBackground
            setPadding(16f.dpToPx(), 12f.dpToPx(), 16f.dpToPx(), 12f.dpToPx())
            val params = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.endToStart = closeViewId
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.marginEnd = 36.dpToPx()
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            setOnClickListener {
                onClickAction()
            }
            it.addView(this, params)
        }
        AppCompatImageView(this@createNewVersionHeader).apply {
            id = closeViewId
            setImageResource(R.drawable.ic_close_round)
            val params = ConstraintLayout.LayoutParams(DeviceUtils.dpToPx(18, this@createNewVersionHeader), DeviceUtils.dpToPx(18, this@createNewVersionHeader))
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.marginEnd = DeviceUtils.dpToPx(8, this@createNewVersionHeader)
            params.topMargin = DeviceUtils.dpToPx(8, this@createNewVersionHeader)
            setOnClickListener {
                onCLickCloseAction()
            }
            it.addView(this, params)
        }
    }
}

@JvmOverloads
fun RecyclerView.addSafeAreaPadding(
    insetsType: Int = WindowInsetsCompat.Type.systemBars(),
    isConsumed: Boolean = false,
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true
) {
    clipToPadding = false
    val initialPaddings = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(
        this
    ) { v, insets ->
        val insetsByType = insets.getInsets(insetsType)
        v.setPadding(
            initialPaddings[0] + (if (left) insetsByType.left else 0),
            initialPaddings[1] + (if (top) insetsByType.top else 0),
            initialPaddings[2] + (if (right) insetsByType.right else 0),
            initialPaddings[3] + (if (bottom) insetsByType.bottom else 0)
        )
        ViewCompat.onApplyWindowInsets(v, if (isConsumed) WindowInsetsCompat.CONSUMED else insets)
    }
}

fun Activity.fixWebViewResettingNightMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        runCatching {
            WebView(this)
        }
    }
}

var currentStreamUrl: String? = null
var currentStreamErrorCount = 0
var currentExternalSource: Int = VideoDetailFragment.EXTERNAL_SOURCE_MAIN
var currentIsOriginPlayer = true
fun logOnOpenVideoDetail(data: OpenVideoDetailData, isOriginPlayer: Boolean) {
    Timber.tag("logEvent").d("logOnOpenVideoDetail: url=${data.url}")
    currentStreamUrl = data.url
    currentStreamErrorCount = 0
    currentExternalSource = data.externalSource
    currentIsOriginPlayer = isOriginPlayer
    logger.logEvent(LogEvent.PLAY_VIDEO)
    logger.logEvent(if (isOriginPlayer) LogEvent.PLAY_VIDEO_ORIGIN else LogEvent.PLAY_VIDEO_BACKUP)
    when (data.externalSource) {
        VideoDetailFragment.EXTERNAL_SOURCE_MAIN -> {
            logger.logEvent(LogEvent.PLAY_VIDEO_MANUAL_HOME)
        }
        VideoDetailFragment.EXTERNAL_SOURCE_SEARCH -> {
            logger.logEvent(LogEvent.PLAY_VIDEO_MANUAL_SEARCH)
        }
        VideoDetailFragment.EXTERNAL_SOURCE_ROUTER -> {
            logger.logEvent(LogEvent.PLAY_VIDEO_ROUTER)
        }
        VideoDetailFragment.EXTERNAL_SOURCE_RECOMMEND -> {
            logger.logEvent(LogEvent.PLAY_VIDEO_RECOMMEND)
        }
    }
}

fun logOnCloseVideoDetail() {
    Timber.tag("logEvent").d("logOnCloseVideoDetail")
    // currentStreamUrl = null
    // currentStreamErrorCount = 0
}

val <I, N> BaseListFragment<I, N>.streamSourceArea: StreamSourceArea
    get() {
        return when (this) {
            is KioskFragment -> StreamSourceArea.Kiosk
            is SearchFragment -> StreamSourceArea.Search
            is RelatedItemsFragment -> StreamSourceArea.Related
            else -> StreamSourceArea.Unknown
        }
    }

val <I, N> BaseListFragment<I, N>.externalSource: Int
    get() {
        return when (streamSourceArea) {
            StreamSourceArea.Kiosk -> VideoDetailFragment.EXTERNAL_SOURCE_MAIN
            StreamSourceArea.Search -> VideoDetailFragment.EXTERNAL_SOURCE_SEARCH
            StreamSourceArea.Related -> VideoDetailFragment.EXTERNAL_SOURCE_RECOMMEND
            StreamSourceArea.Unknown -> VideoDetailFragment.EXTERNAL_SOURCE_OTHER
        }
    }

fun reportStreamError(errorInfo: ErrorInfo, metadata: MediaItemTag?) {
    Timber.d("Stream error: url=${metadata?.streamUrl}, current=$currentStreamUrl")
    if (metadata != null) {
        reportStreamError(metadata.streamUrl)
    }
}

fun reportStreamError(url: String) {
    val isCurrentUrl = run {
        url == currentStreamUrl
    }.takeIf { it } ?: runCatching {
        val id = Uri.parse(url).getQueryParameter("v")
        val currentId = Uri.parse(currentStreamUrl).getQueryParameter("v")
        !id.isNullOrEmpty() && id == currentId
    }.getOrNull() ?: false
    if (isCurrentUrl) {
        Timber.d("Stream error: url=$url, current=$currentStreamUrl")
        if (currentStreamErrorCount++ == 0) {
            logger.logEvent(LogEvent.PLAY_VIDEO_FAILED)
            logger.logEvent(if (currentIsOriginPlayer) LogEvent.PLAY_VIDEO_FAILED_ORIGIN else LogEvent.PLAY_VIDEO_FAILED_BACKUP)
        } else {
            Timber.d("Stream error count: $currentStreamErrorCount")
        }
    }
}

fun org.schabi.newpipe.player.Player.playerOnMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
    ) {
        val current = playQueue?.getItem(exoPlayer.currentMediaItemIndex)
        if (current != null) {
            logOnOpenVideoDetail(
                OpenVideoDetailData(
                    current.serviceId,
                    current.url,
                    current.title,
                    playQueue,
                    true,
                    VideoDetailFragment.EXTERNAL_SOURCE_RECOMMEND
                ),
                true
            )
        }
    }
}
