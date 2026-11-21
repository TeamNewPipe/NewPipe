package org.schabi.newpipe.local.comments

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R

/** Local-only reader; never triggers network extraction or loads additional comment pages. */
object CachedCommentsDialog {
    @JvmStatic
    fun show(context: Context, serviceId: Int, url: String) {
        val app = context.applicationContext
        Single.fromCallable { CommentArchive.read(app, serviceId, url) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ comments ->
                if (comments.isEmpty()) {
                    Toast.makeText(context, R.string.comment_archive_empty, Toast.LENGTH_SHORT).show()
                    return@subscribe
                }
                val density = context.resources.displayMetrics.density
                val padding = (16 * density + 0.5f).toInt()
                val content = TextView(context).apply {
                    text = comments.joinToString("\n\n") { item ->
                        val author = item.author.ifBlank {
                            context.getString(R.string.comment_archive_no_author)
                        }
                        val reply = if (item.parentId != null) {
                            "${context.getString(R.string.comment_archive_reply)} · "
                        } else {
                            ""
                        }
                        "$reply$author · ${item.date}\n${item.body}"
                    }
                    setTextIsSelectable(true)
                    setPadding(padding, padding, padding, padding)
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.comment_archive_saved_title)
                    .setView(NestedScrollView(context).apply { addView(content) })
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }, {
                Toast.makeText(context, R.string.comment_archive_read_failed, Toast.LENGTH_SHORT).show()
            })
    }
}
