package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

/** Uses a custom foreground toast so Android honors the requested placement. */
final class PlayerQualityToast {
    private PlayerQualityToast() { }

    @SuppressWarnings("deprecation")
    static Toast show(final Context context, final String message) {
        final float density = context.getResources().getDisplayMetrics().density;
        final MaterialTextView text = new MaterialTextView(context);
        text.setText(message);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        org.schabi.newpipe.util.AppFonts.apply(text);
        text.setTextColor(MaterialColors.getColor(text,
                com.google.android.material.R.attr.colorOnSurface));
        final int padding = Math.round(16 * density);
        text.setPadding(padding, padding, padding, padding);
        final GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(text,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
        background.setCornerRadius(16 * density);
        text.setBackground(background);
        final boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final Toast toast = new Toast(context);
        toast.setView(text);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity((landscape ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL,
                0, Math.round((landscape ? 48 : 64) * density));
        toast.show();
        return toast;
    }
}
