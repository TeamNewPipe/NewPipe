package org.schabi.newpipe.player.ui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.fragments.list.chat.LiveChatFragment
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.util.DeviceUtils

/** A separate focus window keeps remote navigation and Back inside the comments panel. */
class FullscreenCommentsDialog : DialogFragment() {
    private var videoContent: View? = null
    private var originalEndMargin = 0
    private var originalBottomMargin = 0
    private var ratioOverride: Float? = null
    private var heightRatioOverride: Float? = null
    private var bottomPanel = false
    private var observedRoot: View? = null
    private var backStackListener: FragmentManager.OnBackStackChangedListener? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateViewport() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        ratioOverride = if (savedInstanceState?.containsKey(WIDTH_RATIO_KEY) == true) {
            savedInstanceState.getFloat(WIDTH_RATIO_KEY)
        } else if (prefs.contains(WIDTH_RATIO_KEY)) {
            prefs.getFloat(WIDTH_RATIO_KEY, 0.4f)
        } else {
            null
        }
        heightRatioOverride = if (savedInstanceState?.containsKey(HEIGHT_RATIO_KEY) == true) {
            savedInstanceState.getFloat(HEIGHT_RATIO_KEY)
        } else if (prefs.contains(HEIGHT_RATIO_KEY)) {
            prefs.getFloat(HEIGHT_RATIO_KEY, 0.45f)
        } else {
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        ratioOverride?.let { outState.putFloat(WIDTH_RATIO_KEY, it) }
        heightRatioOverride?.let { outState.putFloat(HEIGHT_RATIO_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.fullscreen_comments_sidebar, container, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.fullscreenCommentsClose).setOnClickListener { dismiss() }
        val handle = view.findViewById<View>(R.id.fullscreenCommentsResizeHandle)
        var startX = 0f
        var startY = 0f
        var startSize = 0
        var resizingBottom = false
        handle.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    resizingBottom = bottomPanel
                    startSize = dialog?.window?.attributes?.let { if (resizingBottom) it.height else it.width } ?: 0
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val parent = activity?.findViewById<View>(R.id.playerVideoContent)?.parent as? View
                    val available = (if (resizingBottom) parent?.height else parent?.width) ?: 0
                    if (resizingBottom != bottomPanel) return@setOnTouchListener true
                    if (available > 0 && startSize > 0) {
                        val delta = if (resizingBottom) {
                            startY - event.rawY
                        } else if (target.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            event.rawX - startX
                        } else {
                            startX - event.rawX
                        }
                        val requestedRatio = (startSize + delta) / available
                        if (resizingBottom) {
                            heightRatioOverride = FullscreenCommentsSizing.height(available, minimumVideoHeight(), requestedRatio).toFloat() / available
                        } else {
                            ratioOverride = FullscreenCommentsSizing.clampWidth((startSize + delta).toInt(), available).toFloat() / available
                        }
                        updateViewport()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val ratio = if (resizingBottom) heightRatioOverride else ratioOverride
                    ratio?.let {
                        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                            .putFloat(if (resizingBottom) HEIGHT_RATIO_KEY else WIDTH_RATIO_KEY, it)
                            .apply()
                    }
                    target.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }
        if (requireArguments().getBoolean(CHAT)) {
            view.findViewById<TextView>(R.id.fullscreenCommentsTitle).setText(R.string.live_chat_title)
        }
        val back = view.findViewById<View>(R.id.fullscreenCommentsBack)
        back.setOnClickListener { childFragmentManager.popBackStack() }
        backStackListener = FragmentManager.OnBackStackChangedListener {
            back.visibility = if (childFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
        }.also {
            childFragmentManager.addOnBackStackChangedListener(it)
        }
        back.visibility = if (childFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
        if (childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) == null) {
            val args = requireArguments()
            childFragmentManager.beginTransaction().replace(
                R.id.fullscreenCommentsContent,
                if (args.getBoolean(CHAT)) {
                    LiveChatFragment.newInstance(args.getInt(SERVICE), args.getString(URL).orEmpty())
                } else {
                    CommentsFragment.getInstance(args.getInt(SERVICE), args.getString(URL).orEmpty(), args.getString(TITLE).orEmpty())
                }
            ).commitNow()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { panel, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            panel.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Keep keyboard/Back focus in this panel, while exposed mobile playback remains
            // touchable. TV retains its established modal D-pad focus and Back contract.
            if (DeviceUtils.isTv(requireContext())) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        observedRoot = requireActivity().window.decorView.also {
            it.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        updateViewport()
        view?.findViewById<View>(R.id.fullscreenCommentsClose)?.requestFocus()
    }

    internal fun showReplies(comment: CommentsInfoItem) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fullscreenCommentsContent, CommentRepliesFragment(comment), CommentRepliesFragment.TAG)
            .addToBackStack(CommentRepliesFragment.TAG)
            .commit()
    }

    private fun updateViewport() {
        if (dialog?.isShowing != true) return
        val window = dialog?.window ?: return
        val content = activity?.findViewById<View>(R.id.playerVideoContent) ?: return
        val parent = content.parent as? View ?: return
        val available = parent.width
        if (available <= 0 || parent.height <= 0) return
        val tv = DeviceUtils.isTv(requireContext())
        val density = resources.displayMetrics.density
        bottomPanel = FullscreenCommentsSizing.useBottomPanel(available, parent.height, density, tv)
        val desired = (TvPlayerFocusPolicy.commentsWidthDp(tv) * density).toInt()
        val width = if (bottomPanel) available else FullscreenCommentsSizing.width(available, desired, ratioOverride)
        val height = if (bottomPanel) FullscreenCommentsSizing.height(parent.height, minimumVideoHeight(), heightRatioOverride) else ViewGroup.LayoutParams.MATCH_PARENT
        val gravity = if (bottomPanel) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.END
        if (window.attributes.gravity != gravity) window.setGravity(gravity)
        if (window.attributes.width != width || window.attributes.height != height) {
            window.setLayout(width, height)
        }
        updateResizeHandle()
        val isLandscapeOverlay = LiveChatLandscapeOverlay.shouldOverlay(
            requireArguments().getBoolean(CHAT),
            parent.width > parent.height,
            tv,
            PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(
                getString(R.string.live_chat_landscape_overlay_key),
                true
            )
        )
        if (isLandscapeOverlay) {
            // Only the chat panel overlays the player; do not shrink the video viewport.
            restoreViewport()
            return
        }
        if (videoContent !== content) {
            restoreViewport()
            videoContent = content
            originalEndMargin = (content.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
            originalBottomMargin = (content.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        }
        val params = content.layoutParams as ViewGroup.MarginLayoutParams
        val end = originalEndMargin + if (bottomPanel) 0 else width
        val bottom = originalBottomMargin + if (bottomPanel) height else 0
        if (params.marginEnd != end || params.bottomMargin != bottom) {
            params.marginEnd = end
            params.bottomMargin = bottom
            content.layoutParams = params
        }
    }

    private fun minimumVideoHeight(): Int {
        val controls = activity?.findViewById<View>(R.id.bottom_player_controls_panel)
        return maxOf(dp(240), (controls?.height ?: 0) + dp(24))
    }

    private fun updateResizeHandle() {
        val root = view ?: return
        val handle = root.findViewById<View>(R.id.fullscreenCommentsResizeHandle)
        val params = handle.layoutParams as FrameLayout.LayoutParams
        val width = if (bottomPanel) ViewGroup.LayoutParams.MATCH_PARENT else dp(24)
        val height = if (bottomPanel) dp(24) else ViewGroup.LayoutParams.MATCH_PARENT
        val gravity = if (bottomPanel) Gravity.TOP else Gravity.START
        if (params.width != width || params.height != height || params.gravity != gravity) {
            params.width = width
            params.height = height
            params.gravity = gravity
            handle.layoutParams = params
        }
        val marker = root.findViewById<View>(R.id.fullscreenCommentsResizeMarker)
        val markerParams = marker.layoutParams
        val markerWidth = dp(if (bottomPanel) 56 else 3)
        val markerHeight = dp(if (bottomPanel) 3 else 56)
        if (markerParams.width != markerWidth || markerParams.height != markerHeight) {
            markerParams.width = markerWidth
            markerParams.height = markerHeight
            marker.layoutParams = markerParams
        }
        val body = root.findViewById<View>(R.id.fullscreenCommentsBody)
        val start = if (bottomPanel) 0 else dp(24)
        val top = if (bottomPanel) dp(24) else 0
        if (body.paddingStart != start || body.paddingTop != top) {
            body.setPaddingRelative(start, top, 0, 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun restoreViewport() {
        videoContent?.let { content ->
            val params = content.layoutParams as ViewGroup.MarginLayoutParams
            params.marginEnd = originalEndMargin
            params.bottomMargin = originalBottomMargin
            content.layoutParams = params
        }
        videoContent = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        restoreViewport()
        super.onDismiss(dialog)
    }

    override fun onStop() {
        observedRoot?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observedRoot = null
        restoreViewport()
        super.onStop()
    }

    override fun onDestroyView() {
        backStackListener?.let(childFragmentManager::removeOnBackStackChangedListener)
        backStackListener = null
        super.onDestroyView()
    }

    companion object {
        private const val CHAT = "live_chat"
        private const val SERVICE = "service"
        private const val URL = "url"
        private const val TITLE = "title"
        private const val WIDTH_RATIO_KEY = "fullscreen_comments_width_ratio"
        private const val HEIGHT_RATIO_KEY = "fullscreen_comments_height_ratio"

        fun newInstance(serviceId: Int, url: String, title: String, liveChat: Boolean = false) = FullscreenCommentsDialog().apply {
            arguments = Bundle().apply {
                putBoolean(CHAT, liveChat)
                putInt(SERVICE, serviceId)
                putString(URL, url)
                putString(TITLE, title)
            }
        }
    }
}
