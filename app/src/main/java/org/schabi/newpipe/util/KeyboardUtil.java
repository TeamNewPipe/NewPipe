package org.schabi.newpipe.util;

import android.app.Activity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

/**
 * Utility class for the Android keyboard.
 * <p>
 * See also <a href="https://stackoverflow.com/q/1109022">https://stackoverflow.com/q/1109022</a>
 * </p>
 */
public final class KeyboardUtil {
    private KeyboardUtil() {
    }

    public static void showKeyboard(final Activity activity, final EditText editText) {
        if (activity == null || editText == null) {
            return;
        }

        if (editText.requestFocus()) {
            final InputMethodManager imm = ContextCompat.getSystemService(activity,
                    InputMethodManager.class);
            if (!imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)) {
                /*
                 * Sometimes the keyboard can't be shown because Android's ImeFocusController is in
                 * a incorrect state e.g. when animations are disabled or the unfocus event of the
                 * previous view arrives in the wrong moment (see #7647 for details).
                 * The invalid state can be fixed by to re-focusing the editText.
                 */
                editText.clearFocus();
                editText.requestFocus();

                // Try again
                imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
            }
        }
    }

    public static void hideKeyboard(final Activity activity, final EditText editText) {
        if (activity == null || editText == null) {
            return;
        }

        final InputMethodManager imm = ContextCompat.getSystemService(activity,
                InputMethodManager.class);
        imm.hideSoftInputFromWindow(editText.getWindowToken(),
                InputMethodManager.RESULT_UNCHANGED_SHOWN);

        editText.clearFocus();
    }
}
