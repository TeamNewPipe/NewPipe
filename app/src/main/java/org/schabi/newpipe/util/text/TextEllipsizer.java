package org.schabi.newpipe.util.text;

import android.graphics.Paint;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.Description;

import java.util.function.Consumer;


import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * <p>Class to ellipsize text inside a {@link TextView}.</p>
 * This class provides all utils to automatically ellipsize and expand a text
 */
public final class TextEllipsizer {
    private static final int EXPANDED_LINES = Integer.MAX_VALUE;
    private static final String ELLIPSIS = "…";

    @NonNull private final CompositeDisposable disposable = new CompositeDisposable();

    @NonNull private final TextView view;
    private final int maxLines;
    @NonNull private Description content;
    @Nullable private StreamingService streamingService;
    @Nullable private String streamUrl;
    private boolean isEllipsized = false;
    @Nullable private Boolean caBeEllipsized = null;

    @NonNull private final Paint paintAtContentSize = new Paint();
    private final float ellipsisWidthPx;
    @Nullable private Consumer<Boolean> stateChangeListener = null;
    @Nullable private Consumer<Boolean> onContentChanged;

    public TextEllipsizer(@NonNull final TextView view,
                          final int maxLines,
                          @Nullable final StreamingService streamingService) {
        this.view = view;
        this.maxLines = maxLines;
        this.streamingService = streamingService;

        paintAtContentSize.setTextSize(view.getTextSize());
        ellipsisWidthPx = paintAtContentSize.measureText(ELLIPSIS);
    }

    public void setOnContentChanged(@Nullable final Consumer<Boolean> onContentChanged) {
        this.onContentChanged = onContentChanged;
    }

    public void setContent(@NonNull final Description content) {
        this.content = content;
        caBeEllipsized = null;
        linkifyContentView(v -> {
            final int currentMaxLines = view.getMaxLines();
            view.setMaxLines(EXPANDED_LINES);
            caBeEllipsized = view.getLineCount() > maxLines;
            view.setMaxLines(currentMaxLines);
            if (onContentChanged != null) {
                onContentChanged.accept(caBeEllipsized);
            }
        });
    }

    public void setStreamUrl(@Nullable final String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public void setStreamingService(@NonNull final StreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Expand the {@link TextEllipsizer#content} to its full length.
     */
    public void expand() {
        view.setMaxLines(EXPANDED_LINES);
        linkifyContentView(v -> isEllipsized = false);
    }

    /**
     * Shorten the {@link TextEllipsizer#content} to the given number of
     * {@link TextEllipsizer#maxLines maximum lines} and add trailing '{@code …}'
     * if the text was shorted.
     */
    public void ellipsize() {
        // expand text to see whether it is necessary to ellipsize the text
        view.setMaxLines(EXPANDED_LINES);
        linkifyContentView(v -> {
            final CharSequence charSeqText = view.getText();
            if (charSeqText != null && view.getLineCount() > maxLines) {
                // Note that converting to String removes spans (i.e. links), but that's something
                // we actually want since when the text is ellipsized we want all clicks on the
                // comment to expand the comment, not to open links.
                final String text = charSeqText.toString();

                final Layout layout = view.getLayout();
                final float lineWidth = layout.getLineWidth(maxLines - 1);
                final float layoutWidth = layout.getWidth();
                final int lineStart = layout.getLineStart(maxLines - 1);
                final int lineEnd = layout.getLineEnd(maxLines - 1);

                // remove characters up until there is enough space for the ellipsis
                // (also summing 2 more pixels, just to be sure to avoid float rounding errors)
                int end = lineEnd;
                float removedCharactersWidth = 0.0f;
                while (lineWidth - removedCharactersWidth + ellipsisWidthPx + 2.0f > layoutWidth
                        && end >= lineStart) {
                    end -= 1;
                    // recalculate each time to account for ligatures or other similar things
                    removedCharactersWidth = paintAtContentSize.measureText(
                            text.substring(end, lineEnd));
                }

                // remove trailing spaces and newlines
                while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
                    end -= 1;
                }

                final String newVal = text.substring(0, end) + ELLIPSIS;
                view.setText(newVal);
                isEllipsized = true;
            } else {
                isEllipsized = false;
            }
            view.setMaxLines(maxLines);
        });
    }

    /**
     * Toggle the view between the ellipsed and expanded state.
     */
    public void toggle() {
        if (isEllipsized) {
            expand();
        } else {
            ellipsize();
        }
    }

    /**
     * Whether the {@link view} can be ellipsized.
     * This is only the case when the {@link content} has more lines
     * than allowed via {@link maxLines}.
     * @return {@code true} if the {@link content} has more lines than allowed via {@link maxLines}
     * and thus can be shortened, {@code false} if the {@code content} fits into the {@link view}
     * without being shortened and {@code null} if the initialization is not completed yet.
     */
    @Nullable
    public Boolean canBeEllipsized() {
        return caBeEllipsized;
    }

    private void linkifyContentView(final Consumer<View> consumer) {
        final boolean oldState = isEllipsized;
        disposable.clear();
        TextLinkifier.fromDescription(view, content,
                HtmlCompat.FROM_HTML_MODE_LEGACY, streamingService, streamUrl, disposable,
                v -> {
                    consumer.accept(v);
                    notifyStateChangeListener(oldState);
                });

    }

    /**
     * Add a listener which is called when the given content is changed,
     * either from <em>ellipsized</em> to <em>full</em> or vice versa.
     * @param listener The listener to be called.
     *                 The Boolean parameter is the new state.
     *                 <em>Ellipsized</em> content is represented as {@code true},
     *                 normal or <em>full</em> content by {@code false}.
     */
    public void setStateChangeListener(final Consumer<Boolean> listener) {
        this.stateChangeListener = listener;
    }

    public void removeStateChangeListener() {
        this.stateChangeListener = null;
    }

    private void notifyStateChangeListener(final boolean oldState) {
        if (oldState != isEllipsized && stateChangeListener != null) {
            stateChangeListener.accept(isEllipsized);
        }
    }

}
