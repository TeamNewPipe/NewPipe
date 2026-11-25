package org.schabi.newpipe.local.subscription

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.databinding.ItemSubscriptionSortBinding
import org.schabi.newpipe.databinding.SubscriptionSortControlsBinding
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ExtractorImageCompat

/** Inline sorting owns the complete profile layout, even when only filtered rows are displayed. */
internal class SubscriptionSortController(
    private val context: Context,
    private val profileId: String,
    private val binding: FragmentSubscriptionBinding,
    savedState: Bundle?,
    private val onSelectionChanged: () -> Unit = {}
) {
    val controls = SubscriptionSortControlsBinding.inflate(LayoutInflater.from(context))
    private val store = SubscriptionLayout(context, profileId)
    private val disposables = CompositeDisposable()
    private val model = SubscriptionSortModel()
    private var channels = emptyMap<String, SubscriptionEntity>()
    private var scope = FeedScope(FeedScope.ALL_SERVICES, 0)
    private var query = ""
    private var rows = emptyList<SubscriptionLayout.Entry>()
    private var pendingAnchor: SubscriptionScrollAnchor? = null
    private var pendingScrollState: Parcelable? = null
    private var loaded = false
    private var dragging = false
    private var dragChanged = false
    private val adapter = SortAdapter()
    private val headerAdapter = ControlsAdapter()
    internal val dragCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int = if (viewHolder.bindingAdapter === adapter) super.getMovementFlags(recyclerView, viewHolder) else 0

        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = current.bindingAdapter === adapter && target.bindingAdapter === adapter

        override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (!loaded || source.bindingAdapter !== adapter || target.bindingAdapter !== adapter) return false
            val from = source.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            val fromKey = rows.getOrNull(from)?.key ?: return false
            val toKey = rows.getOrNull(to)?.key ?: return false
            if (!model.move(fromKey, toKey)) return false
            dragChanged = true
            rebuildRows()
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            dragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            if (dragging) dragChanged = false
            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            dragging = false
            if (dragChanged) persist()
            dragChanged = false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }
    private val touchHelper = ItemTouchHelper(dragCallback)

    init {
        if (savedState?.getString(PROFILE_KEY) == profileId) {
            model.selected.addAll(savedState?.getStringArrayList(SELECTION_KEY).orEmpty())
        }
        binding.subscriptionSortList.layoutManager = LinearLayoutManager(context)
        binding.subscriptionSortList.adapter = ConcatAdapter(headerAdapter, adapter)
        binding.subscriptionSortList.itemAnimator = null
        touchHelper.attachToRecyclerView(binding.subscriptionSortList)
        controls.subscriptionSortSelect.setOnClickListener {
            model.selectVisible(rows.filter { it.separator == null }.map { it.key }.toSet())
            refresh()
        }
        controls.subscriptionSortMove.setOnClickListener { showMoveDialog() }
        controls.subscriptionSortCount.setOnClickListener {
            model.selected.clear()
            refresh()
        }
        refresh()
    }

    fun setEnabled(enabled: Boolean) {
        binding.subscriptionSortPanel.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            disposables.clear()
            model.selected.clear()
            refresh()
            return
        }
        if (disposables.size() != 0) return
        loaded = false
        refresh()
        val manager = SubscriptionManager(context, profileId)
        disposables.add(
            Flowable.combineLatest(
                manager.getSubscriptionsForScope(FeedScope(FeedScope.ALL_SERVICES, 0)),
                store.observe()
            ) { entities, saved -> Pair(entities, saved) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (entities, _) ->
                    // Preference emissions may queue while the user performs another move.
                    // Read the current layout instead of replaying an older queued snapshot.
                    update(entities, store.read(), FeedScope(FeedScope.ALL_SERVICES, 0))
                }, {
                    loaded = false
                    refresh()
                    Toast.makeText(context, R.string.subscription_order_load_failed, Toast.LENGTH_LONG).show()
                })
        )
    }

    internal fun update(entities: List<SubscriptionEntity>, saved: List<SubscriptionLayout.Entry>, currentScope: FeedScope) {
        val updatedChannels = entities.associateBy(SubscriptionLayout::key)
        val changed = model.replace(if (dragging) model.entries else saved, updatedChannels.keys.toList()) || channels != updatedChannels || scope != currentScope || !loaded
        channels = updatedChannels
        scope = currentScope
        loaded = true
        if (changed) refresh()
    }

    fun scrollTo(anchor: SubscriptionScrollAnchor?) {
        pendingScrollState = null
        pendingAnchor = anchor
        if (anchor == null) {
            (binding.subscriptionSortList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        }
        applyPendingAnchor()
    }

    fun scrollAnchor(): SubscriptionScrollAnchor? = SubscriptionScrollAnchor.capture(binding.subscriptionSortList) { position ->
        rows.getOrNull(position - headerAdapter.itemCount)?.key
    }

    private fun applyPendingAnchor() {
        val anchor = pendingAnchor ?: return
        if (!loaded) return
        val position = rows.indexOfFirst { it.key == anchor.key }
        if (position >= 0) {
            (binding.subscriptionSortList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position + headerAdapter.itemCount, anchor.offset)
        }
        pendingAnchor = null
    }

    /** Includes checked channels hidden by the current search, in layout order. */
    fun selectedChannels(): List<SubscriptionEntity> = if (loaded) {
        model.entries.filter { it.separator == null && it.key in model.selected }
            .mapNotNull { channels[it.key] }
    } else {
        emptyList()
    }

    fun setQuery(value: String) {
        query = ContextualSearchHelper.normalizeQuery(value)
        refresh()
    }

    fun saveState(state: Bundle) {
        state.putString(PROFILE_KEY, profileId)
        state.putStringArrayList(SELECTION_KEY, ArrayList(model.selected))
        state.putParcelable(SCROLL_KEY, pendingScrollState ?: binding.subscriptionSortList.layoutManager?.onSaveInstanceState())
    }

    fun restoreScroll(state: Bundle?) {
        @Suppress("DEPRECATION")
        val scroll = state?.getParcelable<Parcelable>(SCROLL_KEY)
        pendingScrollState = scroll
        applyPendingScrollState()
    }

    private fun applyPendingScrollState() {
        if (!loaded) return
        pendingScrollState?.let {
            binding.subscriptionSortList.layoutManager?.onRestoreInstanceState(it)
            pendingScrollState = null
        }
    }

    fun close() {
        disposables.dispose()
        touchHelper.attachToRecyclerView(null)
        binding.subscriptionSortList.adapter = null
        (controls.root.parent as? ViewGroup)?.removeView(controls.root)
    }

    private fun rebuildRows() {
        rows = model.entries.filter { entry ->
            entry.separator != null || channels[entry.key]?.let {
                scope.includes(it) && (SubscriptionSearch.matches(query, it) || ContextualSearchHelper.matches(query, it.url))
            } == true
        }
    }

    private fun refresh() {
        rebuildRows()
        adapter.notifyDataSetChanged()
        adapter.stateRestorationPolicy = if (loaded) RecyclerView.Adapter.StateRestorationPolicy.ALLOW else RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        applyPendingScrollState()
        applyPendingAnchor()
        controls.subscriptionSortCount.text = context.getString(R.string.subscription_sort_selected, model.selected.size)
        controls.subscriptionSortSelect.isEnabled = loaded && rows.any { it.separator == null }
        controls.subscriptionSortMove.isEnabled = loaded && model.selected.isNotEmpty()
        onSelectionChanged()
    }

    private fun persist() {
        if (loaded) store.save(model.entries)
    }

    private fun showMoveDialog() {
        val separators = model.entries.filter { it.separator != null }
        val labels = listOf(context.getString(R.string.subscription_separator_add)) + separators.map { it.separator!! }
        MaterialAlertDialogBuilder(context).setTitle(R.string.subscription_sort_move)
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == 0) {
                    editSeparator(null, moveSelection = true)
                } else if (model.moveSelected(separators[index - 1].key)) {
                    refresh()
                    persist()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    fun editSeparator(entry: SubscriptionLayout.Entry? = null, moveSelection: Boolean = false) {
        if (!loaded) return
        val input = EditText(context).apply {
            hint = context.getString(R.string.subscription_separator_name)
            setSingleLine()
            setText(entry?.separator)
        }
        val builder = MaterialAlertDialogBuilder(context).setTitle(R.string.subscription_separator_name)
            .setView(input).setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
        if (entry != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                model.removeSeparator(entry.key)
                refresh()
                persist()
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = context.getString(R.string.subscription_separator_name)
                return@setOnClickListener
            }
            val key = entry?.key ?: "separator:${UUID.randomUUID()}"
            model.putSeparator(key, name)
            if (moveSelection) model.moveSelected(key)
            refresh()
            persist()
            dialog.dismiss()
        }
    }

    /** Controls share the list's scrolling viewport, including when only one channel row fits. */
    private inner class ControlsAdapter : RecyclerView.Adapter<ControlsHolder>() {
        override fun getItemCount(): Int = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ControlsHolder = ControlsHolder(
            FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )

        override fun onBindViewHolder(holder: ControlsHolder, position: Int) {
            // Each holder owns its container; the controller keeps listeners and header state
            // on the controls when a data refresh replaces or recycles that container.
            if (controls.root.parent !== holder.container) {
                (controls.root.parent as? ViewGroup)?.removeView(controls.root)
                holder.container.addView(controls.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private class ControlsHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    private inner class SortAdapter : RecyclerView.Adapter<SortHolder>() {
        init {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        }

        override fun getItemCount(): Int = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortHolder {
            val holder = SortHolder(ItemSubscriptionSortBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            val row = holder.row
            fun entry(): SubscriptionLayout.Entry? = rows.getOrNull(holder.bindingAdapterPosition)
            fun select() {
                entry()?.let {
                    if (it.separator != null) {
                        editSeparator(it)
                    } else {
                        model.toggle(it.key)
                        refresh()
                    }
                }
            }
            row.root.setOnClickListener { select() }
            row.root.setOnLongClickListener {
                select()
                true
            }
            row.sortSelected.setOnClickListener { select() }
            row.sortDrag.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchHelper.startDrag(holder)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        view.performClick()
                        true
                    }

                    else -> false
                }
            }
            row.sortDrag.setOnClickListener {
                val current = entry() ?: return@setOnClickListener
                MaterialAlertDialogBuilder(context).setTitle(current.separator ?: channels[current.key]?.name)
                    .setItems(arrayOf(context.getString(R.string.subscription_sort_up), context.getString(R.string.subscription_sort_down))) { _, index ->
                        val position = rows.indexOfFirst { it.key == current.key }
                        val target = rows.getOrNull(position + if (index == 0) -1 else 1)
                        if (target != null && model.move(current.key, target.key)) {
                            refresh()
                            persist()
                        }
                    }.show()
            }
            return holder
        }

        override fun onBindViewHolder(holder: SortHolder, position: Int) {
            val entry = rows[position]
            val channel = channels[entry.key]
            holder.row.apply {
                root.isEnabled = loaded
                sortSelected.isEnabled = loaded
                sortDrag.isEnabled = loaded
                sortLabel.text = entry.separator ?: channel?.name.orEmpty()
                sortLabel.setTypeface(null, if (entry.separator != null) Typeface.BOLD else Typeface.NORMAL)
                sortSelected.visibility = if (entry.separator != null) View.INVISIBLE else View.VISIBLE
                sortSelected.isChecked = entry.key in model.selected
                sortSelected.contentDescription = context.getString(R.string.subscription_sort_select_channel, channel?.name.orEmpty())
                sortAvatar.visibility = if (entry.separator != null) View.GONE else View.VISIBLE
                if (channel != null) CoilHelper.loadAvatar(sortAvatar, ExtractorImageCompat.thumbnailImages(channel.toChannelInfoItem()))
            }
        }
    }

    private class SortHolder(val row: ItemSubscriptionSortBinding) : RecyclerView.ViewHolder(row.root)

    companion object {
        private const val SELECTION_KEY = "subscription_sort_selection"
        private const val SCROLL_KEY = "subscription_sort_scroll"
        private const val PROFILE_KEY = "subscription_sort_selection_profile"
    }
}
