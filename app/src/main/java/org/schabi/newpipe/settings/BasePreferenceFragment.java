package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.preferencesearch.PreferenceSearchResultHighlighter;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        final String tag = "androidx.preference.PreferenceFragment.DIALOG";
        if (getParentFragmentManager().findFragmentByTag(tag) != null) {
            return;
        }
        final DialogFragment dialog;
        if (preference instanceof EditTextPreference) {
            dialog = new MaterialEditTextPreferenceDialog();
        } else if (preference instanceof ListPreference) {
            dialog = new MaterialListPreferenceDialog();
        } else if (preference instanceof MultiSelectListPreference) {
            dialog = new MaterialMultiSelectListPreferenceDialog();
        } else {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        final Bundle arguments = new Bundle();
        arguments.putString("key", preference.getKey());
        dialog.setArguments(arguments);
        dialog.setTargetFragment(this, 0);
        dialog.show(getParentFragmentManager(), tag);
    }

    protected void addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(
            @NonNull final PreferenceScreen preferenceScreen) {
        return new CardPreferenceGroupAdapter(preferenceScreen);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        if (getArguments() != null) {
            final String key = getArguments().getString(PreferenceSearchResultHighlighter.ARG_KEY);
            if (key != null) {
                PreferenceSearchResultHighlighter.highlight(key, this);
            }
        }
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
        updateSettingsTitle();
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
        updateSettingsTitle();
    }

    private void updateSettingsTitle() {
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).setSettingsSectionTitle(
                    getPreferenceScreen().getTitle() == null
                            ? getString(R.string.settings) : getPreferenceScreen().getTitle());
        }
    }

    @NonNull
    public final <T extends Preference> T requirePreference(@StringRes final int resId) {
        final T preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }

    private static final class CardPreferenceGroupAdapter extends PreferenceGroupAdapter {
        private final int cardColor;
        private final int rippleColor;
        private final int horizontalMargin;
        private final int cardMarginTop;
        private final int cardMarginBottom;
        private final float cornerRadius;

        CardPreferenceGroupAdapter(@NonNull final PreferenceScreen preferenceScreen) {
            super(preferenceScreen);
            final Context context = preferenceScreen.getContext();
            cardColor = resolveThemeColor(context, R.attr.card_item_background_color);
            rippleColor = resolveThemeColor(context,
                    androidx.appcompat.R.attr.colorControlHighlight);
            horizontalMargin = context.getResources()
                    .getDimensionPixelSize(R.dimen.settings_card_margin_horizontal);
            cardMarginTop = context.getResources()
                    .getDimensionPixelSize(R.dimen.settings_card_margin_top);
            cardMarginBottom = context.getResources()
                    .getDimensionPixelSize(R.dimen.settings_card_margin_bottom);
            cornerRadius = context.getResources()
                    .getDimension(R.dimen.settings_card_corner_radius);
        }

        @Override
        public void onBindViewHolder(@NonNull final PreferenceViewHolder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Preference preference = getItem(position);
            if (isCategory(preference)) {
                holder.itemView.setBackground(null);
                updateMargins(holder.itemView, 0, 0, 0, 0);
                return;
            }

            final boolean hasPreviousCardItem = hasCardItemAt(position - 1);
            final boolean hasNextCardItem = hasCardItemAt(position + 1);

            final float[] radii = getCornerRadii(hasPreviousCardItem, hasNextCardItem);
            holder.itemView.setBackground(createCardBackground(radii));
            updateMargins(holder.itemView, horizontalMargin,
                    hasPreviousCardItem ? 0 : cardMarginTop,
                    horizontalMargin,
                    hasNextCardItem ? 0 : cardMarginBottom);
        }

        private boolean hasCardItemAt(final int position) {
            return position >= 0 && position < getItemCount() && !isCategory(getItem(position));
        }

        private static boolean isCategory(@NonNull final Preference preference) {
            return preference instanceof PreferenceCategory;
        }

        private float[] getCornerRadii(final boolean hasPreviousCardItem,
                                       final boolean hasNextCardItem) {
            final float topRadius = hasPreviousCardItem ? 0 : cornerRadius;
            final float bottomRadius = hasNextCardItem ? 0 : cornerRadius;
            return new float[]{
                    topRadius, topRadius,
                    topRadius, topRadius,
                    bottomRadius, bottomRadius,
                    bottomRadius, bottomRadius
            };
        }

        private RippleDrawable createCardBackground(final float[] radii) {
            final GradientDrawable content = new GradientDrawable();
            content.setColor(cardColor);
            content.setCornerRadii(radii);

            final GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadii(radii);

            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        }

        private static void updateMargins(@NonNull final View view,
                                          final int start,
                                          final int top,
                                          final int end,
                                          final int bottom) {
            final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                final ViewGroup.MarginLayoutParams marginLayoutParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                marginLayoutParams.setMargins(start, top, end, bottom);
                view.setLayoutParams(marginLayoutParams);
            }
        }

        private static int resolveThemeColor(@NonNull final Context context, final int attr) {
            final TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
            final int color = typedArray.getColor(0, 0);
            typedArray.recycle();
            return color;
        }
    }

}
