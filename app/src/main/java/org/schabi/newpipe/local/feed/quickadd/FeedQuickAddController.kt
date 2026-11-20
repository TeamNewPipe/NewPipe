package org.schabi.newpipe.local.feed.quickadd

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.SerialDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Optional
import java.util.WeakHashMap
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.dao.QuickAddStreamKey
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.profiles.ProfileManager

/** One membership subscription for the feed, never one database query per card. */
class FeedQuickAddController(private val list: RecyclerView) {
    private val context = list.context
    private val profileId = ProfileManager.getActiveProfileId(context)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val database = NewPipeDatabase.getInstance(context)
    private val store = QuickAddStore(database, profileId)
    private val watch = SerialDisposable()
    private val writes = CompositeDisposable()
    private val rows = WeakHashMap<View, StreamEntity>()
    private val pending = mutableSetOf<QuickAddStreamKey>()
    private var saved = emptySet<QuickAddStreamKey>()
    private var playlistName: String? = null
    private var targetId = -1L
    private var disposed = false
    private var snackbar: Snackbar? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == QuickAddPreference.targetKey(profileId) || !isCurrentProfile()) refresh()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    private fun isCurrentProfile() = ProfileManager.getActiveProfileId(context) == profileId

    private fun refresh() {
        watch.set(null)
        playlistName = null
        saved = emptySet()
        targetId = if (isCurrentProfile()) preferences.getLong(QuickAddPreference.targetKey(profileId), -1) else -1
        renderAll()
        if (disposed || targetId < 0) return
        watch.set(
            Flowable.combineLatest(
                database.playlistDAO().getPlaylistForProfile(profileId, targetId),
                database.playlistStreamDAO().observeQuickAddStreams(profileId, targetId)
            ) { playlists, streams -> playlists.firstOrNull()?.name to streams.toSet() }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (name, streams) ->
                    playlistName = name
                    saved = streams
                    renderAll()
                }, {
                    playlistName = null
                    renderAll()
                    message(R.string.feed_quick_add_failed)
                })
        )
    }

    fun unbind(row: View) {
        rows.remove(row)
        val container = row.findViewById<FrameLayout>(R.id.itemThumbnailContainer) ?: return
        container.findViewById<View>(R.id.feed_quick_add_button)?.let(container::removeView)
    }

    fun bind(row: View, stream: StreamEntity, eligible: Boolean) {
        unbind(row)
        val container = row.findViewById<FrameLayout>(R.id.itemThumbnailContainer) ?: return
        if (disposed || !eligible) return
        rows[row] = stream
        val button = LayoutInflater.from(context).inflate(R.layout.feed_quick_add_button, container, false) as MaterialButton
        button.setOnClickListener { add(stream) }
        container.addView(button)
        render(row, stream)
    }

    private fun renderAll() = rows.toMap().forEach { (row, stream) -> render(row, stream) }

    private fun render(row: View, stream: StreamEntity) {
        val button = row.findViewById<MaterialButton>(R.id.feed_quick_add_button) ?: return
        val key = QuickAddStreamKey(stream.serviceId, stream.url)
        button.visibility = if (!disposed && playlistName != null && isCurrentProfile()) View.VISIBLE else View.GONE
        button.isEnabled = key !in pending
        button.setIconResource(if (key in saved) R.drawable.ic_playlist_add_check else R.drawable.ic_playlist_add)
        button.contentDescription = context.getString(if (key in saved) R.string.feed_quick_add_saved else R.string.feed_quick_add_action, playlistName.orEmpty())
    }

    private fun add(stream: StreamEntity) {
        val name = playlistName ?: return
        if (disposed || !isCurrentProfile()) return
        val key = QuickAddStreamKey(stream.serviceId, stream.url)
        if (key in saved) {
            snackbar?.dismiss()
            snackbar = Snackbar.make(list, context.getString(R.string.feed_quick_add_saved, name), Snackbar.LENGTH_SHORT).also { it.show() }
            return
        }
        if (!pending.add(key)) return
        val target = targetId
        renderAll()
        writes.add(
            Single.fromCallable { Optional.ofNullable(store.add(target, stream)) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    pending.remove(key)
                    renderAll()
                    if (disposed || !isCurrentProfile()) return@subscribe
                    snackbar?.dismiss()
                    val addition = result.orElse(null)
                    snackbar = Snackbar.make(list, context.getString(if (addition == null) R.string.feed_quick_add_saved else R.string.feed_quick_add_added, name), Snackbar.LENGTH_LONG)
                        .also { bar ->
                            if (addition != null) bar.setAction(R.string.undo) { undo(addition) }
                            bar.show()
                        }
                }, {
                    pending.remove(key)
                    renderAll()
                    message(R.string.feed_quick_add_failed)
                    refresh()
                })
        )
    }

    private fun undo(addition: QuickAddStore.Addition) {
        if (disposed || !isCurrentProfile()) return
        writes.add(
            Single.fromCallable { store.undo(addition) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ if (!it) message(R.string.feed_quick_add_undo_changed) }, { message(R.string.feed_quick_add_failed) })
        )
    }

    private fun message(text: Int) {
        if (!disposed && isCurrentProfile()) {
            snackbar?.dismiss()
            snackbar = Snackbar.make(list, text, Snackbar.LENGTH_LONG).also { it.show() }
        }
    }

    fun dispose() {
        disposed = true
        watch.dispose()
        writes.dispose()
        snackbar?.dismiss()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        rows.clear()
    }
}
