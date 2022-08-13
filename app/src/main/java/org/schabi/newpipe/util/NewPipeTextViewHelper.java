package org.schabi.newpipe.util;

import android.content.Context;
import android.text.Selection;
import android.text.Spannable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.NewPipeEditText;
import org.schabi.newpipe.views.NewPipeTextView;

public final class NewPipeTextViewHelper {
    private NewPipeTextViewHelper() {
    }

    /**
     * Share the selected text of {@link NewPipeTextView NewPipeTextViews} and
     * {@link NewPipeEditText NewPipeEditTexts} with
     * {@link ShareUtils#shareText(Context, String, String)}.
     *
     * <p>
     * This allows EMUI users to get the Android share sheet instead of the EMUI share sheet when
     * using the {@code Share} command of the popup menu which appears when selecting text.
     * </p>
     *
     * @param textView the {@link TextView} on which sharing the selected text. It should be a
     *                 {@link NewPipeTextView} or a {@link NewPipeEditText} (even if
     *                 {@link TextView standard TextViews} are supported).
     */
    public static void shareSelectedTextWithShareUtils(@NonNull final TextView textView) {
        final CharSequence textViewText = textView.getText();
        shareSelectedTextIfNotNullAndNotEmpty(textView, getSelectedText(textView, textViewText));
        if (textViewText instanceof Spannable) {
            Selection.setSelection((Spannable) textViewText, textView.getSelectionEnd());
        }
    }

    @Nullable
    private static CharSequence getSelectedText(@NonNull final TextView textView,
                                                @Nullable final CharSequence text) {
        if (!textView.hasSelection() || text == null) {
            return null;
        }

        final int start = textView.getSelectionStart();
        final int end = textView.getSelectionEnd();
        return String.valueOf(start > end ? text.subSequence(end, start)
                : text.subSequence(start, end));
    }

    private static void shareSelectedTextIfNotNullAndNotEmpty(
            @NonNull final TextView textView,
            @Nullable final CharSequence selectedText) {
        if (selectedText != null && selectedText.length() != 0) {
            ShareUtils.shareText(textView.getContext(), "", selectedText.toString());
        }
    }
}
