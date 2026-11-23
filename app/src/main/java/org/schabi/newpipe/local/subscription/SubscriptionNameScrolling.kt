/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.content.SharedPreferences
import android.text.Layout
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Optional marquee for subscription names, without making the label a separate focus target. */
object SubscriptionNameScrolling {
    fun bind(view: TextView) {
        val binding = view.getTag(R.id.subscription_name_scrolling_binding) as? Binding
            ?: Binding(view).also { view.setTag(R.id.subscription_name_scrolling_binding, it) }
        binding.refresh()
    }

    fun unbind(view: TextView) {
        (view.getTag(R.id.subscription_name_scrolling_binding) as? Binding)?.close()
        view.setTag(R.id.subscription_name_scrolling_binding, null)
    }

    private class Binding(private val view: TextView) :
        View.OnAttachStateChangeListener, View.OnLayoutChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        private val key = view.context.getString(R.string.scroll_subscription_names_key)
        private val originalEllipsize = view.ellipsize
        private val originalSelected = view.isSelected
        private var enabled: Boolean? = null
        private var observing = false

        init {
            view.addOnAttachStateChangeListener(this)
            view.addOnLayoutChangeListener(this)
            if (view.isAttachedToWindow) observe()
        }

        fun refresh() {
            val shouldScroll = preferences.getBoolean(key, false)
            if (enabled != shouldScroll) {
                enabled = shouldScroll
                view.isSelected = false
                // Subscription layouts already constrain names to one line. Only their text
                // layout scrolls; the measured view stays inside its existing row/card bounds.
                view.setHorizontallyScrolling(shouldScroll)
                view.ellipsize = if (shouldScroll) TextUtils.TruncateAt.MARQUEE else originalEllipsize
            }
            val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
            val overflow = shouldScroll && width > 0 && Layout.getDesiredWidth(view.text, view.paint) > width
            val selected = if (shouldScroll) view.isAttachedToWindow && overflow else originalSelected
            if (view.isSelected != selected) view.isSelected = selected
        }

        override fun onViewAttachedToWindow(attached: View) {
            observe()
            refresh()
        }

        override fun onViewDetachedFromWindow(detached: View) {
            stopObserving()
            view.isSelected = false
        }

        override fun onSharedPreferenceChanged(preferences: SharedPreferences, changedKey: String?) {
            if (changedKey == null || changedKey == key) refresh()
        }

        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            refresh()
        }

        private fun observe() {
            if (!observing) {
                preferences.registerOnSharedPreferenceChangeListener(this)
                observing = true
            }
        }

        private fun stopObserving() {
            if (observing) {
                preferences.unregisterOnSharedPreferenceChangeListener(this)
                observing = false
            }
        }

        fun close() {
            stopObserving()
            view.removeOnAttachStateChangeListener(this)
            view.removeOnLayoutChangeListener(this)
            view.isSelected = originalSelected
            view.setHorizontallyScrolling(false)
            view.ellipsize = originalEllipsize
        }
    }
}
