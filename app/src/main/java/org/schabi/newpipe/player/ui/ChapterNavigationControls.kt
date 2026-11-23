/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatImageButton
import java.util.function.LongConsumer
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.download.ChapterTimes
import org.schabi.newpipe.extractor.stream.StreamSegment

/** Chapter navigation belongs to the chapter panel, independently of queue navigation. */
internal class ChapterNavigationControls(private val binding: PlayerBinding) {
    private val originalTitleLayout = RelativeLayout.LayoutParams(binding.itemsListHeaderTitle.layoutParams as RelativeLayout.LayoutParams)
    private val next = button(R.drawable.ic_next, R.string.next_chapter, binding.itemsListClose.id)
    private val previous = button(R.drawable.ic_previous, R.string.previous_chapter, next.id)
    private var previousPosition: Long? = null
    private var nextPosition: Long? = null

    fun show(chapters: List<StreamSegment>, currentMillis: Long, seek: LongConsumer) {
        binding.itemsListHeaderTitle.layoutParams = RelativeLayout.LayoutParams(originalTitleLayout).apply {
            removeRule(RelativeLayout.ALIGN_END)
            addRule(RelativeLayout.START_OF, previous.id)
            marginEnd = 0
        }
        previous.visibility = View.VISIBLE
        next.visibility = View.VISIBLE
        previous.setOnClickListener { previousPosition?.let(seek::accept) }
        next.setOnClickListener { nextPosition?.let(seek::accept) }
        update(chapters, currentMillis)
    }

    fun update(chapters: List<StreamSegment>, currentMillis: Long) {
        previousPosition = ChapterTimes.previous(chapters, currentMillis)
        nextPosition = ChapterTimes.next(chapters, currentMillis)
        previous.isEnabled = previousPosition != null
        next.isEnabled = nextPosition != null
        previous.alpha = if (previous.isEnabled) 1f else 0.35f
        next.alpha = if (next.isEnabled) 1f else 0.35f
    }

    fun hide() {
        previous.visibility = View.GONE
        next.visibility = View.GONE
        previous.setOnClickListener(null)
        next.setOnClickListener(null)
        binding.itemsListHeaderTitle.layoutParams = RelativeLayout.LayoutParams(originalTitleLayout)
    }

    fun clear() {
        hide()
        binding.itemsListControl.removeView(previous)
        binding.itemsListControl.removeView(next)
    }

    private fun button(icon: Int, description: Int, beforeId: Int): AppCompatImageButton {
        val context = binding.itemsListControl.context
        val density = context.resources.displayMetrics.density
        return AppCompatImageButton(context).apply {
            id = View.generateViewId()
            setImageResource(icon)
            contentDescription = context.getString(description)
            val value = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)
            imageTintList = ColorStateList.valueOf(value.data)
            context.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless, value, true)
            setBackgroundResource(value.resourceId)
            val padding = (12 * density).toInt()
            setPadding(padding, padding, padding, padding)
            visibility = View.GONE
            binding.itemsListControl.addView(
                this,
                RelativeLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt()).apply {
                    addRule(RelativeLayout.CENTER_VERTICAL)
                    addRule(RelativeLayout.START_OF, beforeId)
                }
            )
        }
    }
}
