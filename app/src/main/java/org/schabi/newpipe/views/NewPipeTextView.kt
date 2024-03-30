package org.schabi.newpipe.views

import android.content.Context
import android.text.method.MovementMethod
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import org.schabi.newpipe.util.NewPipeTextViewHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * An [AppCompatTextView] which uses [ShareUtils.shareText]
 * when sharing selected text by using the `Share` command of the floating actions.
 *
 *
 *
 * This class allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing
 * text from [AppCompatTextView] on EMUI devices and also to keep movement method set when a
 * text change occurs, if the text cannot be selected and text links are clickable.
 *
 */
class NewPipeTextView : AppCompatTextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context,
                attrs: AttributeSet?,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    public override fun setText(text: CharSequence, type: BufferType) {
        // We need to set again the movement method after a text change because Android resets the
        // movement method to the default one in the case where the text cannot be selected and
        // text links are clickable (which is the default case in NewPipe).
        val movementMethod: MovementMethod = getMovementMethod()
        super.setText(text, type)
        setMovementMethod(movementMethod)
    }

    public override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this)
            return true
        }
        return super.onTextContextMenuItem(id)
    }
}
