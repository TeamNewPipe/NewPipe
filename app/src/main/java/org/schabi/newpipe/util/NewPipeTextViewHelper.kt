package org.schabi.newpipe.util

import android.text.Selection
import android.text.Spannable
import android.widget.TextView
import org.schabi.newpipe.util.external_communication.ShareUtils

object NewPipeTextViewHelper {
    /**
     * Share the selected text of [NewPipeTextViews][NewPipeTextView] and
     * [NewPipeEditTexts][NewPipeEditText] with
     * [ShareUtils.shareText].
     *
     *
     *
     * This allows EMUI users to get the Android share sheet instead of the EMUI share sheet when
     * using the `Share` command of the popup menu which appears when selecting text.
     *
     *
     * @param textView the [TextView] on which sharing the selected text. It should be a
     * [NewPipeTextView] or a [NewPipeEditText] (even if
     * [standard TextViews][TextView] are supported).
     */
    fun shareSelectedTextWithShareUtils(textView: TextView) {
        val textViewText: CharSequence = textView.getText()
        shareSelectedTextIfNotNullAndNotEmpty(textView, getSelectedText(textView, textViewText))
        if (textViewText is Spannable) {
            Selection.setSelection(textViewText as Spannable?, textView.getSelectionEnd())
        }
    }

    private fun getSelectedText(textView: TextView,
                                text: CharSequence?): CharSequence? {
        if (!textView.hasSelection() || text == null) {
            return null
        }
        val start: Int = textView.getSelectionStart()
        val end: Int = textView.getSelectionEnd()
        return (if (start > end) text.subSequence(end, start) else text.subSequence(start, end)).toString()
    }

    private fun shareSelectedTextIfNotNullAndNotEmpty(
            textView: TextView,
            selectedText: CharSequence?) {
        if (selectedText != null && selectedText.length != 0) {
            shareText(textView.getContext(), "", selectedText.toString())
        }
    }
}
