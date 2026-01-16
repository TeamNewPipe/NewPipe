/*
 * SPDX-FileCopyrightText: 2021-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.text.Selection
import android.text.Spannable
import android.widget.TextView
import org.schabi.newpipe.util.external_communication.ShareUtils

object NewPipeTextViewHelper {
    /**
     * Share the selected text of [NewPipeTextViews][org.schabi.newpipe.views.NewPipeTextView] and
     * [NewPipeEditTexts][org.schabi.newpipe.views.NewPipeEditText] with
     * [ShareUtils.shareText].
     *
     *
     *
     * This allows EMUI users to get the Android share sheet instead of the EMUI share sheet when
     * using the `Share` command of the popup menu which appears when selecting text.
     *
     *
     * @param textView the [TextView] on which sharing the selected text. It should be a
     * [org.schabi.newpipe.views.NewPipeTextView] or a [org.schabi.newpipe.views.NewPipeEditText]
     * (even if [standard TextViews][TextView] are supported).
     */
    @JvmStatic
    fun shareSelectedTextWithShareUtils(textView: TextView) {
        val textViewText = textView.getText()
        shareSelectedTextIfNotNullAndNotEmpty(textView, getSelectedText(textView, textViewText))
        if (textViewText is Spannable) {
            Selection.setSelection(textViewText, textView.selectionEnd)
        }
    }

    private fun getSelectedText(textView: TextView, text: CharSequence?): CharSequence? {
        if (!textView.hasSelection() || text == null) {
            return null
        }

        val start = textView.selectionStart
        val end = textView.selectionEnd
        return if (start > end) {
            text.subSequence(end, start)
        } else {
            text.subSequence(start, end)
        }
    }

    private fun shareSelectedTextIfNotNullAndNotEmpty(
        textView: TextView,
        selectedText: CharSequence?
    ) {
        if (!selectedText.isNullOrEmpty()) {
            ShareUtils.shareText(textView.getContext(), "", selectedText.toString())
        }
    }
}
