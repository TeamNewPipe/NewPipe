package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

/** A local, profile-scoped picker; selecting a group does not start a refresh. */
internal class FeedGroupSelector(
    private val context: Context,
    private val actionBar: ActionBar,
    private val selectedId: () -> Long,
    private val onSelected: (Long, String) -> Unit
) {
    private val button = createToolbarView(context)
    private val label = button.findViewById<TextView>(R.id.feed_group_toolbar_label)
    private var groups = emptyList<FeedGroupEntity>()
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private val subscription: Disposable

    init {
        button.isEnabled = false
        button.setOnClickListener { showPicker() }
        subscription = FeedDatabaseManager(context).groups()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ current ->
                groups = current
                // Avoid accepting a stale item if a group is renamed/deleted by sync.
                dialog?.dismiss()
                val selected = groups.firstOrNull { it.uid == selectedId() }
                if (!FeedGroupEntity.isVirtualGroup(selectedId())) {
                    onSelected(selected?.uid ?: FeedGroupEntity.GROUP_ALL_ID, selected?.name.orEmpty())
                }
                button.isEnabled = true
                label.isVisible = true
                updateLabel()
            }, { error -> Log.e("FeedGroupSelector", "Unable to load channel groups", error) })
    }

    private fun updateLabel() {
        val name = if (selectedId() == FeedGroupEntity.GROUP_UNGROUPED_ID) {
            context.getString(R.string.feed_group_ungrouped)
        } else {
            groups.firstOrNull { it.uid == selectedId() }?.name
                ?: context.getString(R.string.feed_group_filter_all)
        }
        label.text = name
        button.contentDescription = context.getString(R.string.fragment_feed_title) + ", " +
            context.getString(R.string.feed_group_filter_description, name)
    }

    fun attach(searching: Boolean = false) {
        actionBar.setCustomView(button, ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        updateSearchVisibility(actionBar, searching)
    }

    fun detach() {
        if (actionBar.customView !== button) return
        actionBar.setDisplayShowCustomEnabled(false)
        actionBar.customView = null
        actionBar.setDisplayShowTitleEnabled(true)
    }

    private fun showPicker() {
        val choices = listOf(FeedGroupEntity.GROUP_ALL_ID to context.getString(R.string.feed_group_filter_all)) +
            groups.map { it.uid to it.name } +
            (FeedGroupEntity.GROUP_UNGROUPED_ID to context.getString(R.string.feed_group_ungrouped))
        val labels = choices.map { it.second }.toTypedArray()
        val selectedIndex = choices.indexOfFirst { it.first == selectedId() }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_groups_header_title)
            .setSingleChoiceItems(labels, selectedIndex) { picker, index ->
                picker.dismiss()
                val (id, name) = choices[index]
                onSelected(id, if (id == FeedGroupEntity.GROUP_ALL_ID) "" else name)
                updateLabel()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        internal fun createToolbarView(context: Context) = LayoutInflater.from(context)
            .inflate(R.layout.feed_group_toolbar, null).apply {
                setTag(R.id.toolbar_custom_title, true)
            }

        @JvmStatic
        fun updateSearchVisibility(actionBar: ActionBar, searching: Boolean) {
            val hasSelector = actionBar.customView?.id == R.id.feed_group_toolbar
            actionBar.setDisplayShowTitleEnabled(!searching && !hasSelector)
            if (hasSelector) actionBar.setDisplayShowCustomEnabled(!searching)
        }
    }

    fun dispose() {
        detach()
        subscription.dispose()
        dialog?.dismiss()
        dialog = null
        button.setOnClickListener(null)
    }
}
