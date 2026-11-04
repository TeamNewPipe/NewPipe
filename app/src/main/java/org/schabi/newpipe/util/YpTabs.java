/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.R;

/**
 * YewPlay: text tabs as in the proposal. The tab layout re-applies its own text style (caps,
 * two lines), so each label is a custom view: sentence case, a single line, Instrument Sans
 * SemiBold, ivory/ink when selected and warm gray otherwise.
 */
public final class YpTabs {
    private YpTabs() {
    }

    /**
     * Sets the label of a tab.
     *
     * @param tab  the tab
     * @param name the label
     */
    public static void setLabel(@NonNull final TabLayout.Tab tab, @NonNull final CharSequence name) {
        final TabLayout parent = tab.parent;
        if (parent == null) {
            return;
        }
        final TextView label = new TextView(parent.getContext());
        label.setText(name);
        label.setAllCaps(false);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTypeface(ResourcesCompat.getFont(parent.getContext(),
                R.font.instrument_sans_semibold));
        label.setTextColor(ContextCompat.getColorStateList(parent.getContext(),
                R.color.yp_tab_text));
        tab.setText(name);
        tab.setCustomView(label);
    }
}
