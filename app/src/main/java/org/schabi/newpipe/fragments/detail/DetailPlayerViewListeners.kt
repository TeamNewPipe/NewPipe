/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior

/** Keeps activity-owned player callbacks within the lifetime of one detail view. */
@MainThread
class DetailPlayerViewListeners(
    owner: LifecycleOwner,
    private val behavior: BottomSheetBehavior<*>,
    private val fragmentManager: FragmentManager,
    callback: BottomSheetBehavior.BottomSheetCallback,
    onBackStackChanged: Runnable
) : DefaultLifecycleObserver, AutoCloseable {
    private val lifecycle = owner.lifecycle
    private var sheetDelegate: BottomSheetBehavior.BottomSheetCallback? = callback
    private var backStackDelegate: Runnable? = onBackStackChanged
    private var closed = false
    private val sheetListener = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            sheetDelegate?.onStateChanged(bottomSheet, newState)
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            sheetDelegate?.onSlide(bottomSheet, slideOffset)
        }
    }
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        backStackDelegate?.run()
    }

    init {
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
            close()
        } else {
            behavior.addBottomSheetCallback(sheetListener)
            fragmentManager.addOnBackStackChangedListener(backStackListener)
            lifecycle.addObserver(this)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) = close()

    override fun close() {
        if (closed) return
        closed = true
        // Dispatch may already hold a listener snapshot when the view is destroyed.
        // Drop captured view/fragment callbacks before unregistering either wrapper.
        sheetDelegate = null
        backStackDelegate = null
        behavior.removeBottomSheetCallback(sheetListener)
        fragmentManager.removeOnBackStackChangedListener(backStackListener)
        lifecycle.removeObserver(this)
    }
}
