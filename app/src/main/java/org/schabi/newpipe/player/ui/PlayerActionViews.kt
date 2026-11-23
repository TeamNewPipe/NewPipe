/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import org.schabi.newpipe.views.NewPipeTextView

/** The original control keeps its ID, listeners, layout parameters and availability updates. */
class PlayerActionImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.imageButtonStyle
) : AppCompatImageButton(context, attrs, defStyleAttr) {
    // View constructors may call setVisibility before this delegate has been initialized.
    private val actionVisibility: PlayerActionVisibilityDelegate? =
        PlayerActionVisibilityDelegate(this) { super.setVisibility(it) }.also { it.refresh() }

    override fun setVisibility(visibility: Int) {
        actionVisibility?.setVisibility(visibility) ?: super.setVisibility(visibility)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        actionVisibility?.attach()
    }

    override fun onDetachedFromWindow() {
        actionVisibility?.detach()
        super.onDetachedFromWindow()
    }
}

open class PlayerActionTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : NewPipeTextView(context, attrs, defStyleAttr) {
    private val actionVisibility: PlayerActionVisibilityDelegate? =
        PlayerActionVisibilityDelegate(this) { super.setVisibility(it) }.also { it.refresh() }

    override fun setVisibility(visibility: Int) {
        actionVisibility?.setVisibility(visibility) ?: super.setVisibility(visibility)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        actionVisibility?.attach()
    }

    override fun onDetachedFromWindow() {
        actionVisibility?.detach()
        super.onDetachedFromWindow()
    }
}
