package org.schabi.newpipe.ui.components.common

import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.core.text.getSpans

// The code below is copied from Html.android.kt in the Compose Text library, with some minor
// changes.

internal fun Spanned.toAnnotatedString(
    linkStyles: TextLinkStyles? = null,
    linkInteractionListener: LinkInteractionListener? = null
): AnnotatedString {
    return AnnotatedString.Builder(capacity = length)
        .append(this)
        .also {
            it.addSpans(this, linkStyles, linkInteractionListener)
        }
        .toAnnotatedString()
}

private fun AnnotatedString.Builder.addSpans(
    spanned: Spanned,
    linkStyles: TextLinkStyles?,
    linkInteractionListener: LinkInteractionListener?
) {
    spanned.getSpans<Any>().forEach { span ->
        addSpan(
            span,
            spanned.getSpanStart(span),
            spanned.getSpanEnd(span),
            linkStyles,
            linkInteractionListener
        )
    }
}

private fun AnnotatedString.Builder.addSpan(
    span: Any,
    start: Int,
    end: Int,
    linkStyles: TextLinkStyles?,
    linkInteractionListener: LinkInteractionListener?
) {
    when (span) {
        is AbsoluteSizeSpan -> {
            // TODO: Add Compose's implementation when it is available.
        }

        is AlignmentSpan -> {
            addStyle(span.toParagraphStyle(), start, end)
        }

        is BackgroundColorSpan -> {
            addStyle(SpanStyle(background = Color(span.backgroundColor)), start, end)
        }

        is ForegroundColorSpan -> {
            addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
        }

        is RelativeSizeSpan -> {
            addStyle(SpanStyle(fontSize = span.sizeChange.em), start, end)
        }

        is StrikethroughSpan -> {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
        }

        is StyleSpan -> {
            span.toSpanStyle()?.let { addStyle(it, start, end) }
        }

        is SubscriptSpan -> {
            addStyle(SpanStyle(baselineShift = BaselineShift.Subscript), start, end)
        }

        is SuperscriptSpan -> {
            addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), start, end)
        }

        is TypefaceSpan -> {
            addStyle(span.toSpanStyle(), start, end)
        }

        is UnderlineSpan -> {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }

        is URLSpan -> {
            span.url?.let { url ->
                val link = LinkAnnotation.Url(url, linkStyles, linkInteractionListener)
                addLink(link, start, end)
            }
        }
    }
}

private fun AlignmentSpan.toParagraphStyle(): ParagraphStyle {
    val alignment = when (this.alignment) {
        Layout.Alignment.ALIGN_NORMAL -> TextAlign.Start
        Layout.Alignment.ALIGN_CENTER -> TextAlign.Center
        Layout.Alignment.ALIGN_OPPOSITE -> TextAlign.End
        else -> TextAlign.Unspecified
    }
    return ParagraphStyle(textAlign = alignment)
}

private fun StyleSpan.toSpanStyle(): SpanStyle? {
    return when (style) {
        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        else -> null
    }
}

private fun TypefaceSpan.toSpanStyle(): SpanStyle {
    val fontFamily = when (family) {
        FontFamily.Cursive.name -> FontFamily.Cursive
        FontFamily.Monospace.name -> FontFamily.Monospace
        FontFamily.SansSerif.name -> FontFamily.SansSerif
        FontFamily.Serif.name -> FontFamily.Serif
        else -> {
            optionalFontFamilyFromName(family)
        }
    }
    return SpanStyle(fontFamily = fontFamily)
}

private fun optionalFontFamilyFromName(familyName: String?): FontFamily? {
    if (familyName.isNullOrEmpty()) return null
    val typeface = Typeface.create(familyName, Typeface.NORMAL)
    return typeface.takeIf {
        typeface != Typeface.DEFAULT &&
            typeface != Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }?.let { FontFamily(it) }
}
