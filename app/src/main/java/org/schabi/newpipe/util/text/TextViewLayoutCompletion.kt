/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.text

import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable

/** Waits for current text measurements; clearing the binding cancels a pending completion. */
object TextViewLayoutCompletion {
    @JvmStatic
    @MainThread
    fun afterLayout(view: TextView, disposables: CompositeDisposable, completion: Runnable) {
        if (disposables.isDisposed) return
        if (view.isLaidOut && !view.isLayoutRequested && hasTextLayout(view)) {
            completion.run()
            return
        }

        lateinit var pending: Disposable
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (hasTextLayout(view) && disposables.remove(pending)) {
                completion.run()
            }
        }
        pending = Disposable.fromAction { view.removeOnLayoutChangeListener(listener) }
        view.addOnLayoutChangeListener(listener)
        disposables.add(pending)
    }

    private fun hasTextLayout(view: TextView): Boolean = view.width > 0 && view.layout != null
}
