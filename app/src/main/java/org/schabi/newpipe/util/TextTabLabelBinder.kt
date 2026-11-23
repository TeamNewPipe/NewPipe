/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import com.google.android.material.tabs.TabLayout

/** Configure text tabs explicitly because Material pools tabs across different screens. */
object TextTabLabelBinder {
    @JvmStatic
    fun bind(tab: TabLayout.Tab, title: CharSequence) {
        // Material 1.11 retains label visibility when it recycles an icon-only Home tab.
        tab.setTabLabelVisibility(TabLayout.TAB_LABEL_VISIBILITY_LABELED)
        tab.setText(title)
        tab.setContentDescription(title)
    }
}
