package org.schabi.newpipe.views;

import android.content.Context;
import android.text.Selection;
import android.text.Spannable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.schabi.newpipe.util.external_communication.ShareUtils;

/**
 * An {@link AppCompatEditText} which uses {@link ShareUtils#shareText(Context, String, String)}
 * when sharing selected text by using the {@code Share} command of the floating actions.
 * <p>
 * This allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing text
 * from {@link AppCompatEditText} on EMUI devices.
 * </p>
 */
public class NewPipeEditText extends AppCompatEditText {

    public NewPipeEditText(@NonNull final Context context) {
        super(context);
    }

    public NewPipeEditText(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public NewPipeEditText(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTextContextMenuItem(final int id) {
        final Spannable text = getText();
        if (id == android.R.id.shareText) {
            if (text != null) {
                final String selectedText = getSelectedText(text).toString();
                if (!selectedText.isEmpty()) {
                    ShareUtils.shareText(getContext(), "", selectedText);
                }
                Selection.setSelection(text, getSelectionEnd());
            }
            return true;
        } else {
            return super.onTextContextMenuItem(id);
        }
    }

    @NonNull
    private CharSequence getSelectedText(@NonNull final CharSequence charSequence) {
        int min = 0;
        int max = charSequence.length();

        if (isFocused()) {
            final int selStart = getSelectionStart();
            final int selEnd = getSelectionEnd();

            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }
        return charSequence.subSequence(min, max);
    }
}
