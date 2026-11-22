package org.schabi.newpipe.player

import android.content.Context
import android.text.format.DateUtils
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R

/** Offline timeline viewer for a user-captured chat. No network access or account required. */
object LiveChatRecordingDialog {
    @JvmStatic
    fun show(context: Context, serviceId: Int, url: String) {
        val app = context.applicationContext
        Single.fromCallable { LiveChatRecorder.read(app, serviceId, url) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ rows ->
                if (rows.isEmpty()) {
                    Toast.makeText(context, R.string.live_chat_capture_empty, Toast.LENGTH_LONG).show()
                    return@subscribe
                }
                val max = rows.maxOf(LiveChatRecorder.Entry::atMillis).coerceAtLeast(1)
                val text = TextView(context).apply {
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setTextIsSelectable(true)
                }
                fun update(at: Long) {
                    text.text = LiveChatRecorder.replayRows(rows, at).joinToString("\n\n") { message ->
                        val offset = DateUtils.formatElapsedTime(message.atMillis / 1000)
                        val author = message.author.ifBlank {
                            context.getString(R.string.live_chat_capture_unknown)
                        }
                        "[$offset] $author: ${message.text}"
                    }
                }
                val seek = SeekBar(context).apply {
                    this.max = (max / 1000).toInt().coerceAtLeast(1)
                    progress = this.max
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                            if (fromUser) update(progress.toLong() * 1000)
                        }
                        override fun onStartTrackingTouch(bar: SeekBar) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar) = Unit
                    })
                }
                update(max)
                val panel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(seek)
                    addView(NestedScrollView(context).apply { addView(text) })
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.live_chat_capture_title)
                    .setView(panel)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }, {
                Toast.makeText(context, R.string.live_chat_capture_read_failure, Toast.LENGTH_SHORT).show()
            })
    }
}
