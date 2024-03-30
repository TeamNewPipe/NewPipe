package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import org.schabi.newpipe.util.NewPipeTextViewHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * An [AppCompatEditText] which uses [ShareUtils.shareText]
 * when sharing selected text by using the `Share` command of the floating actions.
 *
 *
 *
 * This class allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing
 * text from [AppCompatEditText] on EMUI devices.
 *
 */
class NewPipeEditText : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context,
                attrs: AttributeSet?,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    public override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this)
            return true
        }
        return super.onTextContextMenuItem(id)
    }
}
