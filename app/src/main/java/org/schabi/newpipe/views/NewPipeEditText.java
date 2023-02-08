package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.schabi.newpipe.util.NewPipeTextViewHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

/**
 * An {@link AppCompatEditText} which uses {@link ShareUtils#shareText(Context, String, String)}
 * when sharing selected text by using the {@code Share} command of the floating actions.
 *
 * <p>
 * This class allows NewPipe to show Android share sheet instead of EMUI share sheet when sharing
 * text from {@link AppCompatEditText} on EMUI devices.
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
        if (id == android.R.id.shareText) {
            NewPipeTextViewHelper.shareSelectedTextWithShareUtils(this);
            return true;
        }
        return super.onTextContextMenuItem(id);
    }
}
