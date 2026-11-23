/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

/** Keep the full instructions available without taking the row viewport away at large font sizes. */
class HomeTabsHelpAdapter : RecyclerView.Adapter<HomeTabsHelpAdapter.Holder>() {
    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.list_choose_tabs_help, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    class Holder(view: View) : RecyclerView.ViewHolder(view)
}
