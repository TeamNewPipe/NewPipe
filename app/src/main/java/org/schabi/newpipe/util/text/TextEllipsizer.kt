package org.schabi.newpipe.util.text

import android.graphics.Paint
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.Description
import java.util.function.Consumer

/**
 *
 * Class to ellipsize text inside a [TextView].
 * This class provides all utils to automatically ellipsize and expand a text
 */
class TextEllipsizer(private val view: TextView,
                     private val maxLines: Int,
                     private var streamingService: StreamingService?) {
    private val disposable = CompositeDisposable()
    private var content: Description
    private var streamUrl: String? = null
    private var isEllipsized = false
    private var canBeEllipsized: Boolean? = null
    private val paintAtContentSize = Paint()
    private val ellipsisWidthPx: Float
    private var stateChangeListener: Consumer<Boolean>? = null
    private var onContentChanged: Consumer<Boolean>? = null

    init {
        content = Description.EMPTY_DESCRIPTION
        paintAtContentSize.textSize = view.textSize
        ellipsisWidthPx = paintAtContentSize.measureText(ELLIPSIS)
    }

    fun setOnContentChanged(onContentChanged: Consumer<Boolean>?) {
        this.onContentChanged = onContentChanged
    }

    fun setContent(content: Description) {
        this.content = content
        canBeEllipsized = null
        linkifyContentView { v: View? ->
            val currentMaxLines = view.maxLines
            view.setMaxLines(EXPANDED_LINES)
            canBeEllipsized = view.lineCount > maxLines
            view.setMaxLines(currentMaxLines)
            if (onContentChanged != null) {
                onContentChanged!!.accept(canBeEllipsized!!)
            }
        }
    }

    fun setStreamUrl(streamUrl: String?) {
        this.streamUrl = streamUrl
    }

    fun setStreamingService(streamingService: StreamingService) {
        this.streamingService = streamingService
    }

    /**
     * Expand the [TextEllipsizer.content] to its full length.
     */
    fun expand() {
        view.setMaxLines(EXPANDED_LINES)
        linkifyContentView { v: View? -> isEllipsized = false }
    }

    /**
     * Shorten the [TextEllipsizer.content] to the given number of
     * [maximum lines][maxLines] and add trailing '`…`'
     * if the text was shorted.
     */
    fun ellipsize() {
        // expand text to see whether it is necessary to ellipsize the text
        view.setMaxLines(EXPANDED_LINES)
        linkifyContentView { v: View? ->
            val charSeqText = view.getText()
            if (charSeqText != null && view.lineCount > maxLines) {
                // Note that converting to String removes spans (i.e. links), but that's something
                // we actually want since when the text is ellipsized we want all clicks on the
                // comment to expand the comment, not to open links.
                val text = charSeqText.toString()
                val layout = view.layout
                val lineWidth = layout.getLineWidth(maxLines - 1)
                val layoutWidth = layout.width.toFloat()
                val lineStart = layout.getLineStart(maxLines - 1)
                val lineEnd = layout.getLineEnd(maxLines - 1)

                // remove characters up until there is enough space for the ellipsis
                // (also summing 2 more pixels, just to be sure to avoid float rounding errors)
                var end = lineEnd
                var removedCharactersWidth = 0.0f
                while (lineWidth - removedCharactersWidth + ellipsisWidthPx + 2.0f > layoutWidth
                        && end >= lineStart) {
                    end -= 1
                    // recalculate each time to account for ligatures or other similar things
                    removedCharactersWidth = paintAtContentSize.measureText(
                            text.substring(end, lineEnd))
                }

                // remove trailing spaces and newlines
                while (end > 0 && Character.isWhitespace(text[end - 1])) {
                    end -= 1
                }
                val newVal = text.substring(0, end) + ELLIPSIS
                view.text = newVal
                isEllipsized = true
            } else {
                isEllipsized = false
            }
            view.setMaxLines(maxLines)
        }
    }

    /**
     * Toggle the view between the ellipsized and expanded state.
     */
    fun toggle() {
        if (isEllipsized) {
            expand()
        } else {
            ellipsize()
        }
    }

    /**
     * Whether the [.view] can be ellipsized.
     * This is only the case when the [.content] has more lines
     * than allowed via [.maxLines].
     * @return `true` if the [.content] has more lines than allowed via
     * [.maxLines] and thus can be shortened, `false` if the `content` fits into
     * the [.view] without being shortened and `null` if the initialization is not
     * completed yet.
     */
    fun canBeEllipsized(): Boolean? {
        return canBeEllipsized
    }

    private fun linkifyContentView(consumer: Consumer<View?>) {
        val oldState = isEllipsized
        disposable.clear()
        TextLinkifier.fromDescription(view, content,
                HtmlCompat.FROM_HTML_MODE_LEGACY, streamingService, streamUrl, disposable
        ) { v: TextView? ->
            consumer.accept(v)
            notifyStateChangeListener(oldState)
        }
    }

    /**
     * Add a listener which is called when the given content is changed,
     * either from *ellipsized* to *full* or vice versa.
     * @param listener The listener to be called, or `null` to remove it.
     * The Boolean parameter is the new state.
     * *Ellipsized* content is represented as `true`,
     * normal or *full* content by `false`.
     */
    fun setStateChangeListener(listener: Consumer<Boolean>?) {
        stateChangeListener = listener
    }

    private fun notifyStateChangeListener(oldState: Boolean) {
        if (oldState != isEllipsized && stateChangeListener != null) {
            stateChangeListener!!.accept(isEllipsized)
        }
    }

    companion object {
        private const val EXPANDED_LINES = Int.MAX_VALUE
        private const val ELLIPSIS = "…"
    }
}
