package org.schabi.newpipe.util.text

import android.content.res.Resources
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Patterns
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.text.parseAsHtml
import androidx.core.text.toHtml
import androidx.core.text.toSpanned

/**
 * Takes in a CharSequence [text]
 * and makes raw HTTP URLs and HTML anchor tags clickable
 */
fun TextView.setTextWithLinks(text: CharSequence) {
    val spanned = SpannableString(text)
    // Using the pattern overload of addLinks since the one with the int masks strips all spans from the text before applying new ones
    Linkify.addLinks(spanned, Patterns.WEB_URL, null)
    this.text = spanned
    this.movementMethod = LinkMovementMethod.getInstance()
}

/**
 * Gets text from string resource with [id] while preserving styling and allowing string format value substitution of [formatArgs]
 */
fun Resources.getText(@StringRes id: Int, vararg formatArgs: Any?): CharSequence = getText(id).toSpanned().toHtml().format(*formatArgs).parseAsHtml()
