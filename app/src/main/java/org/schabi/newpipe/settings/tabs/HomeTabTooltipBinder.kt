/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.tabs

import androidx.annotation.DrawableRes
import com.google.android.material.tabs.TabLayout

/** Keep icon-only Home tabs while assigning their actual titles to long-press tooltips. */
object HomeTabTooltipBinder {
    @JvmStatic
    fun bind(tab: TabLayout.Tab, title: CharSequence, @DrawableRes icon: Int) {
        // A content description alone does not necessarily override a tab's tooltip text.
        // Update the backing title and hide the text label without hiding it from accessibility.
        tab.setText(title)
        tab.setTabLabelVisibility(TabLayout.TAB_LABEL_VISIBILITY_UNLABELED)
        tab.setContentDescription(title)
        tab.setIcon(icon)
    }
}
