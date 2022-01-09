package org.schabi.newpipe.settings.preferencesearch;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;


public final class PreferenceSearchResultHighlighter {
    private static final String TAG = "PrefSearchResHighlter";

    private PreferenceSearchResultHighlighter() {
    }

    /**
     * Highlight the specified preference.
     * <br/>
     * Note: This function is Thread independent (can be called from outside of the main thread).
     *
     * @param item The item to highlight
     * @param prefsFragment The fragment where the items is located on
     */
    public static void highlight(
            final PreferenceSearchItem item,
            final PreferenceFragmentCompat prefsFragment
    ) {
        new Handler(Looper.getMainLooper()).post(() -> doHighlight(item, prefsFragment));
    }

    private static void doHighlight(
            final PreferenceSearchItem item,
            final PreferenceFragmentCompat prefsFragment
    ) {
        final Preference prefResult = prefsFragment.findPreference(item.getKey());

        if (prefResult == null) {
            Log.w(TAG, "Preference '" + item.getKey() + "' not found on '" + prefsFragment + "'");
            return;
        }

        final RecyclerView recyclerView = prefsFragment.getListView();
        final RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter instanceof PreferenceGroup.PreferencePositionCallback) {
            final int position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                    .getPreferenceAdapterPosition(prefResult);
            if (position != RecyclerView.NO_POSITION) {
                recyclerView.scrollToPosition(position);
                recyclerView.postDelayed(() -> {
                    final RecyclerView.ViewHolder holder =
                            recyclerView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        final Drawable background = holder.itemView.getBackground();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                && background instanceof RippleDrawable) {
                            showRippleAnimation((RippleDrawable) background);
                            return;
                        }
                    }
                    highlightFallback(prefsFragment, prefResult);
                }, 200);
                return;
            }
        }
        highlightFallback(prefsFragment, prefResult);
    }

    /**
     * Alternative highlighting (shows an → arrow in front of the setting)if ripple does not work.
     *
     * @param prefsFragment
     * @param prefResult
     */
    private static void highlightFallback(
            final PreferenceFragmentCompat prefsFragment,
            final Preference prefResult
    ) {
        // Get primary color from text for highlight icon
        final TypedValue typedValue = new TypedValue();
        final Resources.Theme theme = prefsFragment.getActivity().getTheme();
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        final TypedArray arr = prefsFragment.getActivity()
                .obtainStyledAttributes(
                        typedValue.data,
                        new int[]{android.R.attr.textColorPrimary});
        final int color = arr.getColor(0, 0xffE53935);
        arr.recycle();

        // Show highlight icon
        final Drawable oldIcon = prefResult.getIcon();
        final boolean oldSpaceReserved = prefResult.isIconSpaceReserved();
        final Drawable highlightIcon =
                AppCompatResources.getDrawable(
                        prefsFragment.requireContext(),
                        R.drawable.ic_play_arrow);
        highlightIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        prefResult.setIcon(highlightIcon);

        prefsFragment.scrollToPreference(prefResult);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            prefResult.setIcon(oldIcon);
            prefResult.setIconSpaceReserved(oldSpaceReserved);
        }, 1000);
    }

    private static void showRippleAnimation(final RippleDrawable rippleDrawable) {
        rippleDrawable.setState(
                new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
        new Handler(Looper.getMainLooper())
                .postDelayed(() -> rippleDrawable.setState(new int[]{}), 1000);
    }
}
