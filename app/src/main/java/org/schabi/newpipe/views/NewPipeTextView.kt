package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import org.schabi.newpipe.util.NewPipeTextViewHelper

/**
 * An [AppCompatTextView] which uses [org.schabi.newpipe.util.external_communication.ShareUtils.shareText]
 * when sharing selected text by using the `Share` command of the floating actions.
 *
 * This class allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing
 * text from [AppCompatTextView] on EMUI devices and also to keep movement method set when a
 * text change occurs, if the text cannot be selected and text links are clickable.
 */
class NewPipeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun setText(text: CharSequence?, type: BufferType?) {
        // We need to set again the movement method after a text change because Android resets the
        // movement method to the default one in the case where the text cannot be selected and
        // text links are clickable (which is the default case in NewPipe).
        val movementMethod = movementMethod
        super.setText(text, type)
        setMovementMethod(movementMethod)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this)
            return true
        }
        return super.onTextContextMenuItem(id)
    }
}
