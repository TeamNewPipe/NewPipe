package org.schabi.newpipe.fragments.list.chat

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.player.LiveChatRecorder
import org.schabi.newpipe.player.LiveChatRecordingDialog
import org.schabi.newpipe.player.LiveChatRepository
import org.schabi.newpipe.util.ContentBlockingHelper

/** Read-only live chat. No requests while this tab is hidden or the app is backgrounded. */
class LiveChatFragment : Fragment() {
    private var collection: Job? = null
    private var overlayChosen = false
    private val modeListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (context?.getString(R.string.danmaku_enabled_key) == key) {
            view?.post {
                overlayChosen = preferences.getBoolean(key, false)
                if (overlayChosen) {
                    stop()
                    status?.setText(R.string.live_chat_overlay_active)
                } else {
                    startIfVisible()
                }
            }
        }
    }
    private val visibilityListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (view?.isShown == true) startIfVisible() else stop()
    }
    private val rows = ArrayList<String>()
    private var adapter: ArrayAdapter<String>? = null
    private var status: TextView? = null
    private var list: ListView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.live_chat_panel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val source = requireArguments()
        val chatService = source.getInt(SERVICE)
        val chatUrl = source.getString(URL).orEmpty()
        val captureButton = view.findViewById<Button>(R.id.liveChatCapture)
        val savedButton = view.findViewById<Button>(R.id.liveChatSaved)
        fun updateButtons() {
            val capturing = LiveChatRecorder.isRecording(chatService, chatUrl)
            captureButton.setText(
                if (capturing) R.string.live_chat_capture_stop else R.string.live_chat_capture_start
            )
            savedButton.setText(
                if (LiveChatRecorder.hasSaved(requireContext(), chatService, chatUrl)) {
                    R.string.live_chat_saved_badge
                } else {
                    R.string.live_chat_saved_open
                }
            )
        }
        captureButton.setOnClickListener {
            if (LiveChatRecorder.isRecording(chatService, chatUrl)) {
                LiveChatRecorder.stop(chatService, chatUrl)
            } else if (!LiveChatRecorder.start(requireContext(), chatService, chatUrl)) {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.live_chat_capture_busy,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            updateButtons()
        }
        savedButton.setOnClickListener {
            LiveChatRecordingDialog.show(requireContext(), chatService, chatUrl)
        }
        updateButtons()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(modeListener)
        view.findViewById<Button>(R.id.liveChatDanmaku).setOnClickListener {
            overlayChosen = true
            stop()
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean(getString(R.string.danmaku_enabled_key), true).apply()
            status?.setText(R.string.live_chat_overlay_active)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(visibilityListener)
        status = view.findViewById(R.id.liveChatStatus)
        list = view.findViewById<ListView>(R.id.liveChatMessages).also {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rows)
            it.adapter = adapter
        }
        view.findViewById<Button>(R.id.liveChatRetry).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                collection?.cancelAndJoin()
                collection = null
                overlayChosen = false
                val args = requireArguments()
                LiveChatRepository.retry(args.getInt(SERVICE), args.getString(URL).orEmpty())
                startIfVisible()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(visible: Boolean) {
        super.setUserVisibleHint(visible)
        if (visible) {
            startIfVisible()
        } else {
            stop()
        }
    }

    override fun onResume() {
        super.onResume()
        val args = arguments
        if (args != null && view != null) {
            val service = args.getInt(SERVICE)
            val link = args.getString(URL).orEmpty()
            view?.findViewById<Button>(R.id.liveChatCapture)?.setText(
                if (LiveChatRecorder.isRecording(service, link)) {
                    R.string.live_chat_capture_stop
                } else {
                    R.string.live_chat_capture_start
                }
            )
        }
        startIfVisible()
    }

    override fun onPause() {
        stop()
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun startIfVisible() {
        if (overlayChosen || !isResumed || !userVisibleHint || view?.isShown != true || collection != null) return
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
            .putBoolean(getString(R.string.danmaku_enabled_key), false).apply()
        status?.setText(R.string.live_chat_waiting)
        val args = requireArguments()
        collection = viewLifecycleOwner.lifecycleScope.launch {
            LiveChatRepository.messages(args.getInt(SERVICE), args.getString(URL).orEmpty()).collect { result ->
                result.fold(
                    onSuccess = { messages ->
                        status?.setText(R.string.live_chat_read_only)
                        append(messages)
                    },
                    onFailure = { status?.setText(R.string.live_chat_unavailable) }
                )
            }
        }
    }

    private fun append(messages: List<BulletCommentsInfoItem>) {
        if (messages.isEmpty()) return
        val follow = list?.let { it.lastVisiblePosition >= rows.lastIndex - 1 } ?: true
        val rules = ContentBlockingHelper.getRules(requireContext(), ContentBlockingHelper.Target.COMMENTS)
        messages.forEach { message ->
            val author = message.name.orEmpty().trim()
            val text = message.commentText.orEmpty().trim()
            val comment = CommentsInfoItem(message.serviceId, message.url.orEmpty(), text).apply {
                commentText = text
                uploaderName = author
                likeCount = CommentsInfoItem.NO_LIKE_COUNT
            }
            if (text.isNotEmpty() && !rules.isBlocked(comment)) {
                rows.add(if (author.isEmpty()) text else "$author: $text")
            }
        }
        while (rows.size > 200) rows.removeAt(0)
        adapter?.notifyDataSetChanged()
        status?.setText(R.string.live_chat_read_only)
        if (follow && rows.isNotEmpty()) list?.setSelection(rows.lastIndex)
    }

    private fun stop() {
        collection?.cancel()
        collection = null
    }

    override fun onDestroyView() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(modeListener)
        view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(visibilityListener)
        stop()
        adapter = null
        list = null
        status = null
        rows.clear()
        super.onDestroyView()
    }

    /** Identifies the extractor stream for reuse when the detail panel switches videos. */
    fun matchesSource(serviceId: Int, url: String): Boolean = arguments?.let {
        it.getInt(SERVICE) == serviceId && it.getString(URL) == url
    } == true

    companion object {
        private const val SERVICE = "service"
        private const val URL = "url"

        @JvmStatic
        fun newInstance(serviceId: Int, url: String) = LiveChatFragment().apply {
            arguments = Bundle().apply {
                putInt(SERVICE, serviceId)
                putString(URL, url)
            }
        }
    }
}
