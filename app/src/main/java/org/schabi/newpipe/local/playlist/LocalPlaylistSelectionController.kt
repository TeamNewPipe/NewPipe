/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.WeakHashMap
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.LongSupplier
import java.util.function.Supplier
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.download.BulkDownloadDialog
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.ListSelectionOrder
import org.schabi.newpipe.local.ListSeparators
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper

/** Batch actions for own playlists, preserving the complete saved order behind filtered views. */
class LocalPlaylistSelectionController(
    private val fragment: Fragment,
    private val recycler: RecyclerView,
    private val adapter: LocalItemListAdapter,
    private val button: View,
    private val profileId: String,
    private val playlistId: LongSupplier,
    private val savedItems: Supplier<List<PlaylistStreamEntry>>,
    private val dirty: BooleanSupplier,
    private val saveGate: PlaylistSelectionSaveGate,
    private val save: Runnable,
    private val rebind: Consumer<List<PlaylistStreamEntry>>
) : ActionMode.Callback, AutoCloseable {
    private val activity = fragment.requireActivity() as AppCompatActivity
    private val context = activity.applicationContext
    private val boundPlaylistId = playlistId.asLong
    private val database = NewPipeDatabase.getInstance(context)
    private val manager = LocalPlaylistManager(database, profileId)
    private val store = context.let { appContext -> PlaylistSelectionStore(database) { ProfileManager.getActiveProfileId(appContext) } }
    private val separators = ListSeparators(context, profileId, "playlist:$boundPlaylistId")
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val disposables = CompositeDisposable()
    private var mode: ActionMode? = null
    private var dialog: AlertDialog? = null
    private var snapshot: PlaylistSelectionSnapshot? = null
    private var source = emptyList<PlaylistStreamEntry>()
    private var headings = emptyMap<Int, String>()
    private var pendingStart = false
    private var waitingForSaves = false
    private var loading = false
    private var writing = false
    private var closed = false
    private val previousDescriptions = WeakHashMap<View, CharSequence?>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) {
            recycler.post {
                if (!closed && !samePlaylist()) {
                    pendingStart = false
                    finishMode()
                    updateButton()
                }
            }
        }
    }
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = onRowsChanged()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onRowsChanged()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onRowsChanged()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = onRowsChanged()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onRowsChanged()
    }
    private val decoration = object : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val selection = snapshot ?: return
            val primary = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimary)
            val surface = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorSurface)
            val onPrimary = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnPrimary)
            val density = parent.resources.displayMetrics.density
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                val itemIndex = adapter.getItemIndex(parent.getChildAdapterPosition(child))
                val entry = adapter.itemsList.getOrNull(itemIndex) as? PlaylistStreamEntry ?: continue
                val checked = selection.isSelected(entry)
                if (!previousDescriptions.containsKey(child)) previousDescriptions[child] = ViewCompat.getStateDescription(child)
                child.isSelected = checked
                ViewCompat.setStateDescription(child, activity.getString(if (checked) R.string.playlist_selection_selected else R.string.playlist_selection_not_selected))
                paint.style = Paint.Style.FILL
                if (checked) {
                    paint.color = ColorUtils.setAlphaComponent(primary, 45)
                    canvas.drawRect(child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat(), paint)
                }
                val size = 22 * density
                val right = if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) child.left + 12 * density + size else child.right - 12 * density
                val top = child.top + 12 * density
                paint.color = if (checked) primary else surface
                canvas.drawRoundRect(right - size, top, right, top + size, 3 * density, 3 * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2 * density
                paint.color = if (checked) onPrimary else primary
                if (checked) {
                    canvas.drawLine(right - 17 * density, top + 11 * density, right - 10 * density, top + 17 * density, paint)
                    canvas.drawLine(right - 10 * density, top + 17 * density, right - 4 * density, top + 5 * density, paint)
                } else {
                    canvas.drawRoundRect(right - size, top, right, top + size, 3 * density, 3 * density, paint)
                }
            }
        }
    }

    init {
        button.visibility = View.VISIBLE
        button.setOnClickListener { requestSelection() }
        adapter.registerAdapterDataObserver(observer)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateButton()
    }

    private fun samePlaylist(): Boolean = playlistId.asLong == boundPlaylistId && ProfileManager.getActiveProfileId(context) == profileId

    private fun displayed(): List<PlaylistStreamEntry> = adapter.itemsList.filterIsInstance<PlaylistStreamEntry>()

    private fun updateButton() {
        button.isEnabled = !closed && !writing && !pendingStart && !loading && samePlaylist() && displayed().isNotEmpty()
    }

    private fun requestSelection() {
        if (closed || writing || mode != null || !samePlaylist()) return
        pendingStart = true
        continueSelectionRequest()
    }

    private fun continueSelectionRequest() {
        updateButton()
        if (!pendingStart || closed || loading || writing || !samePlaylist()) return
        if (saveGate.hasPendingWrites) {
            if (!waitingForSaves) {
                waitingForSaves = true
                disposables.add(
                    saveGate.awaitIdle().observeOn(AndroidSchedulers.mainThread()).subscribe {
                        waitingForSaves = false
                        if (!closed) continueSelectionRequest()
                    }
                )
            }
            return
        }
        if (dirty.asBoolean) {
            save.run()
            return
        }
        val saved = savedItems.get().toList()
        val visible = displayed()
        if (visible.isEmpty()) {
            pendingStart = false
            updateButton()
            return
        }
        val requested = PlaylistSelectionSnapshot(profileId, boundPlaylistId, saved, visible)
        loading = true
        disposables.add(
            manager.getPlaylistStreams(boundPlaylistId).firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ persisted ->
                    loading = false
                    if (closed || !pendingStart || !samePlaylist()) return@subscribe
                    if (saveGate.hasPendingWrites || dirty.asBoolean) {
                        continueSelectionRequest()
                        return@subscribe
                    }
                    if (!requested.isCurrent(profileId, boundPlaylistId, savedItems.get(), displayed()) || persisted.map { it.streamId } != saved.map { it.streamId }) {
                        pendingStart = false
                        rebind.accept(persisted)
                        changed()
                        updateButton()
                        return@subscribe
                    }
                    // A prior edit may have reindexed occurrences; always start with fresh DB rows.
                    rebind.accept(persisted)
                    pendingStart = false
                    source = savedItems.get().toList()
                    headings = separators.labels(source).toMap()
                    snapshot = PlaylistSelectionSnapshot(profileId, boundPlaylistId, source, displayed())
                    mode = activity.startSupportActionMode(this)
                    if (mode != null) {
                        recycler.addItemDecoration(decoration)
                        adapter.setSelectionToggle { item ->
                            if (!writing && item is PlaylistStreamEntry && ensureCurrent()) {
                                snapshot?.toggle(item)
                                refreshSelection()
                            }
                        }
                        refreshSelection()
                    }
                    updateButton()
                }, { error ->
                    loading = false
                    pendingStart = false
                    if (!closed) {
                        updateButton()
                        ErrorUtil.showUiErrorSnackbar(fragment, "Preparing playlist selection", error)
                    }
                })
        )
    }

    fun onSaveSettled() {
        recycler.post { if (!closed) continueSelectionRequest() }
    }

    fun onSaveFailed() {
        pendingStart = false
        updateButton()
    }

    fun isWriting(): Boolean = writing

    /** Editing commands finish selection; a committed batch must finish before another edit. */
    fun finishBeforeChange(): Boolean {
        if (writing) {
            Toast.makeText(activity, R.string.playlist_selection_saving, Toast.LENGTH_SHORT).show()
            return false
        }
        pendingStart = false
        finishMode()
        updateButton()
        return true
    }

    private fun onRowsChanged() {
        if (mode != null && !writing && !current()) {
            finishMode()
            changed()
        }
        updateButton()
    }

    private fun current(): Boolean = !closed && samePlaylist() && !dirty.asBoolean && !saveGate.hasPendingWrites &&
        snapshot?.isCurrent(profileId, boundPlaylistId, savedItems.get(), displayed()) == true && separators.labels(source) == headings

    private fun ensureCurrent(): Boolean {
        if (current()) return true
        finishMode()
        changed()
        return false
    }

    private fun changed() {
        if (!closed) Toast.makeText(activity, R.string.collection_changed_retry, Toast.LENGTH_LONG).show()
    }

    private fun refreshSelection() {
        mode?.title = activity.getString(R.string.collection_selected, snapshot?.selectedCount ?: 0)
        mode?.invalidate()
        recycler.invalidateItemDecorations()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(0, R.id.selection_all, 0, R.string.collection_select_all)
            .setIcon(R.drawable.ic_select_all).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, R.id.playlist_selection_remove, 1, R.string.playlist_remove_selected).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, R.id.playlist_selection_move, 2, R.string.playlist_reorder_selected).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, R.id.selection_playlist, 3, R.string.playlist_copy_to_playlist)
        menu.add(0, R.id.playlist_selection_transfer, 4, R.string.playlist_move_to_playlist)
        menu.add(0, R.id.selection_enqueue, 5, R.string.enqueue_stream)
        menu.add(0, R.id.selection_download, 6, R.string.playlist_download_selected)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selected = snapshot?.selectedInDisplayOrder().orEmpty()
        for (index in 0 until menu.size()) menu.getItem(index).isEnabled = !writing && (menu.getItem(index).itemId == R.id.selection_all || selected.isNotEmpty())
        menu.findItem(R.id.selection_download).isEnabled = !writing && selected.any(::downloadable)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, action: MenuItem): Boolean {
        if (writing || !ensureCurrent()) return true
        val selection = snapshot ?: return true
        if (action.itemId == R.id.selection_all) {
            selection.toggleAllDisplayed()
            refreshSelection()
            return true
        }
        val selected = selection.selectedInDisplayOrder()
        if (selected.isEmpty()) return true
        when (action.itemId) {
            R.id.playlist_selection_remove -> confirmRemoval(selection)

            R.id.playlist_selection_move -> showDestinations(selection)

            R.id.playlist_selection_transfer -> showTransferDestinations(selection)

            R.id.selection_enqueue -> {
                NavigationHelper.enqueueOnPlayer(activity, LocalMediaPlayQueue(selected.map { it.toPlayQueueItem() }, 0))
                finishMode()
            }

            R.id.selection_playlist -> {
                disposables.add(
                    PlaylistDialog.createCorrespondingDialog(activity, selected.map { it.streamEntity.copy() }) { created ->
                        if (!closed && samePlaylist() && fragment.isAdded && !fragment.parentFragmentManager.isStateSaved) {
                            created.show(fragment.parentFragmentManager, "PlaylistDialog")
                        }
                    }
                )
                finishMode()
            }

            R.id.selection_download -> {
                val eligible = selected.filter(::downloadable)
                if (eligible.isNotEmpty()) {
                    val skipped = selected.size - eligible.size
                    if (skipped > 0) Toast.makeText(activity, activity.resources.getQuantityString(R.plurals.playlist_selection_download_skipped, skipped, skipped), Toast.LENGTH_LONG).show()
                    BulkDownloadDialog.newInstance(LocalPlaylistBulkDownloadMapper.from(eligible)).show(fragment.parentFragmentManager, "BulkDownloadDialog")
                    finishMode()
                }
            }

            else -> return false
        }
        return true
    }

    private fun confirmRemoval(selection: PlaylistSelectionSnapshot) {
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.playlist_remove_selected)
            .setMessage(activity.resources.getQuantityString(R.plurals.playlist_remove_selected_confirmation, selection.selectedCount, selection.selectedCount))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.playlist_selection_remove_confirm) { _, _ ->
                if (!ensureCurrent()) return@setPositiveButton
                val selectedIndices = selection.selectedSavedIndices()
                var item = 0
                val removedRows = (0 until source.size + headings.size).filter { row -> row !in headings && item++ in selectedIndices }
                val nextHeadings = headings.mapKeys { (row, _) -> row - removedRows.count { it < row } }
                commit(selection.remainingSavedOrder(), nextHeadings)
            }.show()
    }

    private fun showDestinations(selection: PlaylistSelectionSnapshot) {
        val destinations = listOf(
            activity.getString(R.string.collection_move_start) to 0,
            activity.getString(R.string.collection_move_end) to (source.size + headings.size)
        ) + headings.map { (row, label) -> label to row + 1 }
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.subscription_sort_move)
            .setItems(destinations.map { it.first }.toTypedArray()) { _, index ->
                if (!ensureCurrent()) return@setItems
                val moved = ListSelectionOrder.move(source.size, headings, selection.selectedSavedIndices(), destinations[index].second)
                commit(moved.itemIndices.map(source::get), moved.labels)
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showTransferDestinations(selection: PlaylistSelectionSnapshot) {
        writing = true
        updateButton()
        refreshSelection()
        disposables.add(
            manager.playlists.firstOrError().observeOn(AndroidSchedulers.mainThread())
                .subscribe({ playlists ->
                    writing = false
                    if (closed || !ensureCurrent()) return@subscribe
                    refreshSelection()
                    updateButton()
                    val destinations = playlists.filter { it.uid != boundPlaylistId }
                    dialog?.dismiss()
                    val builder = MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.playlist_move_to_playlist)
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.create_playlist) { _, _ -> showNewTransferPlaylist(selection) }
                    if (destinations.isEmpty()) {
                        builder.setMessage(R.string.playlist_transfer_no_destination)
                    } else {
                        builder.setItems(destinations.map { it.orderingName.orEmpty() }.toTypedArray()) { _, index ->
                            moveToPlaylist(selection, PlaylistTransferDestination.Existing(destinations[index].uid))
                        }
                    }
                    dialog = builder.show()
                }, { error ->
                    writing = false
                    if (!closed) {
                        finishMode()
                        updateButton()
                        ErrorUtil.showUiErrorSnackbar(fragment, "Loading destination playlists", error)
                    }
                })
        )
    }

    private fun showNewTransferPlaylist(selection: PlaylistSelectionSnapshot) {
        if (!ensureCurrent()) return
        val binding = DialogEditTextBinding.inflate(LayoutInflater.from(activity))
        binding.dialogEditText.setHint(R.string.name)
        binding.dialogEditText.setSingleLine()
        val nameDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.create_playlist)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.subscription_sort_move, null)
            .create()
        dialog = nameDialog
        nameDialog.setOnShowListener {
            nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.dialogEditText.text.toString().trim()
                if (name.isEmpty()) {
                    binding.dialogEditText.error = activity.getString(R.string.playlist_transfer_name_required)
                } else {
                    nameDialog.dismiss()
                    moveToPlaylist(selection, PlaylistTransferDestination.Create(name))
                }
            }
        }
        nameDialog.show()
    }

    private fun moveToPlaylist(selection: PlaylistSelectionSnapshot, destination: PlaylistTransferDestination) {
        if (writing || !ensureCurrent()) return
        val sourceIndices = java.util.IdentityHashMap<PlaylistStreamEntry, Int>()
        source.forEachIndexed { index, entry -> sourceIndices[entry] = index }
        val selected = selection.selectedInDisplayOrder().map { entry -> checkNotNull(sourceIndices[entry]) }
        val selectedSet = selected.toSet()
        val replacement = selection.remainingSavedOrder()
        var item = 0
        val removedRows = (0 until source.size + headings.size).filter { row -> row !in headings && item++ in selectedSet }
        val nextHeadings = headings.mapKeys { (row, _) -> row - removedRows.count { it < row } }
        writing = true
        updateButton()
        refreshSelection()
        disposables.add(
            store.moveToPlaylist(profileId, boundPlaylistId, source, selected, destination, separators, headings, nextHeadings)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ accepted ->
                    if (closed) return@subscribe
                    if (!accepted) changed()
                    finishCommit(accepted, replacement)
                }, { error ->
                    writing = false
                    if (!closed) {
                        finishMode()
                        updateButton()
                        ErrorUtil.showUiErrorSnackbar(fragment, "Moving selected playlist items", error)
                    }
                })
        )
    }

    private fun commit(replacement: List<PlaylistStreamEntry>, nextHeadings: Map<Int, String>) {
        if (writing || !ensureCurrent()) return
        writing = true
        updateButton()
        refreshSelection()
        disposables.add(
            store.replaceWithHeadings(profileId, boundPlaylistId, source, replacement, separators, headings, nextHeadings)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ accepted ->
                    if (closed) return@subscribe
                    if (!accepted) changed()
                    finishCommit(accepted, replacement)
                }, { error ->
                    writing = false
                    if (!closed) {
                        finishMode()
                        updateButton()
                        ErrorUtil.showUiErrorSnackbar(fragment, "Updating selected playlist items", error)
                    }
                })
        )
    }

    private fun finishCommit(accepted: Boolean, replacement: List<PlaylistStreamEntry>) {
        if (!samePlaylist()) {
            writing = false
            finishMode()
            updateButton()
            return
        }
        disposables.add(
            manager.getPlaylistStreams(boundPlaylistId).firstOrError().observeOn(AndroidSchedulers.mainThread())
                .subscribe({ rows ->
                    if (closed) return@subscribe
                    // Keep every edit gesture blocked until the committed full order is visible.
                    if (samePlaylist()) rebind.accept(rows)
                    writing = false
                    finishMode()
                    updateButton()
                }, { error ->
                    if (!closed) {
                        if (samePlaylist()) {
                            // Never leave removed rows available to a later legacy save after a reload error.
                            rebind.accept(if (accepted) replacement.mapIndexed { index, entry -> entry.copy(joinIndex = index) } else emptyList())
                        }
                        writing = false
                        finishMode()
                        updateButton()
                        ErrorUtil.showUiErrorSnackbar(fragment, "Reloading playlist selection", error)
                    }
                })
        )
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.mode = null
        dialog?.dismiss()
        dialog = null
        recycler.removeItemDecoration(decoration)
        previousDescriptions.forEach { (view, description) ->
            view.isSelected = false
            ViewCompat.setStateDescription(view, description)
        }
        previousDescriptions.clear()
        snapshot = null
        // Back may close the toolbar before its accepted edit commits. Keep gestures blocked.
        if ((!writing || closed) && adapter.isSelecting) adapter.setSelectionToggle(null)
        updateButton()
    }

    private fun finishMode() {
        mode?.finish()
        if (mode == null && (!writing || closed) && adapter.isSelecting) adapter.setSelectionToggle(null)
    }

    override fun close() {
        closed = true
        pendingStart = false
        finishMode()
        dialog?.dismiss()
        disposables.dispose()
        adapter.unregisterAdapterDataObserver(observer)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        button.setOnClickListener(null)
    }

    companion object {
        @JvmStatic
        fun savedOrder(adapter: LocalItemListAdapter?, unfiltered: List<PlaylistStreamEntry>, query: String, sort: String): List<PlaylistStreamEntry> = if (ContextualSearchHelper.isActive(query) || LocalPlaylistSortMode.fromName(sort) != LocalPlaylistSortMode.PLAYLIST_ORDER) {
            unfiltered.toList()
        } else {
            adapter?.itemsList?.filterIsInstance<PlaylistStreamEntry>().orEmpty()
        }

        internal fun downloadable(entry: PlaylistStreamEntry): Boolean = entry.streamEntity.let {
            !it.isLocalMedia && it.serviceId >= 0 && (it.streamType == StreamType.VIDEO_STREAM || it.streamType == StreamType.AUDIO_STREAM) &&
                (it.url.startsWith("https://", true) || it.url.startsWith("http://", true))
        }
    }
}
