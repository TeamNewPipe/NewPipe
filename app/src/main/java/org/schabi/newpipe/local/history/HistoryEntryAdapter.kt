package org.schabi.newpipe.local.history

import android.content.Context
import android.view.View
import android.view.View.OnLongClickListener
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.util.Localization
import java.text.DateFormat
import java.util.Date

/**
 * This is an adapter for history entries.
 *
 * @param <E>  the type of the entries
 * @param <VH> the type of the view holder
</VH></E> */
abstract class HistoryEntryAdapter<E, VH : RecyclerView.ViewHolder?>(private val mContext: Context) : RecyclerView.Adapter<VH>() {
    private val mEntries: ArrayList<E>
    private val mDateFormat: DateFormat
    private var onHistoryItemClickListener: OnHistoryItemClickListener<E>? = null

    init {
        mEntries = ArrayList()
        mDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM,
                Localization.getPreferredLocale(mContext))
    }

    fun setEntries(historyEntries: Collection<E>) {
        mEntries.clear()
        mEntries.addAll(historyEntries)
        notifyDataSetChanged()
    }

    val items: Collection<E>
        get() {
            return mEntries
        }

    fun clear() {
        mEntries.clear()
        notifyDataSetChanged()
    }

    protected fun getFormattedDate(date: Date?): String {
        return mDateFormat.format(date)
    }

    protected fun getFormattedViewString(viewCount: Long): String? {
        return Localization.shortViewCount(mContext, viewCount)
    }

    public override fun getItemCount(): Int {
        return mEntries.size
    }

    public override fun onBindViewHolder(holder: VH, position: Int) {
        val entry: E = mEntries.get(position)
        holder!!.itemView.setOnClickListener(View.OnClickListener({ v: View? ->
            if (onHistoryItemClickListener != null) {
                onHistoryItemClickListener!!.onHistoryItemClick(entry)
            }
        }))
        holder.itemView.setOnLongClickListener(OnLongClickListener({ view: View? ->
            if (onHistoryItemClickListener != null) {
                onHistoryItemClickListener!!.onHistoryItemLongClick(entry)
                return@setOnLongClickListener true
            }
            false
        }))
        onBindViewHolder(holder, entry, position)
    }

    public override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder!!.itemView.setOnClickListener(null)
    }

    abstract fun onBindViewHolder(holder: VH, entry: E, position: Int)
    fun setOnHistoryItemClickListener(
            onHistoryItemClickListener: OnHistoryItemClickListener<E>?) {
        this.onHistoryItemClickListener = onHistoryItemClickListener
    }

    val isEmpty: Boolean
        get() {
            return mEntries.isEmpty()
        }

    open interface OnHistoryItemClickListener<E> {
        fun onHistoryItemClick(item: E)
        fun onHistoryItemLongClick(item: E)
    }
}
