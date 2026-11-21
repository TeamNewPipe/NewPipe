/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.schabi.newpipe.R

/** Bottom-start overlay, above progress and away from top-right note/disclosure badges. */
object DownloadedBadgeBinder {
    private const val TAG = "wizestream_downloaded_badge"

    @JvmStatic
    fun bind(thumbnail: View, serviceId: Int, sourceUrl: String) {
        val container = thumbnail.parent as? FrameLayout ?: return
        val existing = container.findViewWithTag<ImageView>(TAG)
        val visible = DownloadedAvailabilityIndex.isAvailable(serviceId, sourceUrl)
        if (!visible && existing == null) return
        val badge = existing ?: ImageView(container.context).apply {
            tag = TAG
            setImageResource(R.drawable.ic_file_download)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            val density = resources.displayMetrics.density
            val side = (23f * density).toInt()
            val margin = (5f * density).toInt()
            val pad = (4f * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 20, 20, 20))
                cornerRadius = 6f * density
            }
            contentDescription = context.getString(R.string.downloaded_available_offline)
            isClickable = false
            isFocusable = false
            val params = FrameLayout.LayoutParams(side, side, Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = (10f * density).toInt()
                marginStart = margin
            }
            container.addView(this, params)
        }
        badge.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
