// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.schabi.newpipe.extractor.search.filter.FilterContainer;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;

import static org.schabi.newpipe.fragments.list.search.filter.InjectFilterItem.DividerItem;

public class SearchFilterDialogSpinnerAdapter extends BaseAdapter {

    private final Context context;
    private final FilterGroup group;
    private final BaseSearchFilterUiGenerator.UiWrapperMapDelegate wrapperDelegate;
    private final Spinner spinner;
    private final SparseIntArray id2PosMap = new SparseIntArray();
    private final SparseArrayCompat<UiItemWrapperSpinner>
            viewWrapperMap = new SparseArrayCompat<>();

    public SearchFilterDialogSpinnerAdapter(
            @NonNull final Context context,
            @NonNull final FilterGroup group,
            @NonNull final BaseSearchFilterUiGenerator.UiWrapperMapDelegate wrapperDelegate,
            @NonNull final Spinner filterDataSpinner) {
        this.context = context;
        this.group = group;
        this.wrapperDelegate = wrapperDelegate;
        this.spinner = filterDataSpinner;

        createViewWrappers();
    }

    @Override
    public int getCount() {
        return group.getFilterItems().size();
    }

    @Override
    public Object getItem(final int position) {
        return group.getFilterItems().get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final FilterItem item = group.getFilterItems().get(position);
        final TextView view;

        if (convertView != null) {
            view = (TextView) convertView;
        } else {
            view = createViewItem();
        }

        initViewWithData(position, item, view);
        return view;
    }

    @SuppressLint("WrongConstant")
    private void initViewWithData(final int position,
                                  final FilterItem item,
                                  final TextView view) {
        final UiItemWrapperSpinner wrappedView =
                viewWrapperMap.get(position);
        Objects.requireNonNull(wrappedView);

        view.setId(item.getIdentifier());
        view.setText(ServiceHelper.getTranslatedFilterString(item.getNameId(), context));
        view.setVisibility(wrappedView.getVisibility());
        view.setEnabled(wrappedView.isEnabled());

        if (item instanceof DividerItem) {
            final DividerItem dividerItem = (DividerItem) item;
            wrappedView.setEnabled(false);
            view.setEnabled(wrappedView.isEnabled());
            final String menuDividerTitle = ">>>"
                    + context.getString(dividerItem.getStringResId()) + "<<<";
            view.setText(menuDividerTitle);
        }
    }

    private void createViewWrappers() {
        int position = 0;
        for (final FilterItem item : this.group.getFilterItems()) {
            final int initialVisibility = View.VISIBLE;
            final boolean isInitialEnabled = true;

            final UiItemWrapperSpinner wrappedView =
                    new UiItemWrapperSpinner(
                            item,
                            initialVisibility,
                            isInitialEnabled,
                            spinner);

            if (item instanceof DividerItem) {
                wrappedView.setEnabled(false);
            }

            // store wrapper also locally as we refer here regularly
            viewWrapperMap.put(position, wrappedView);
            // store wrapper globally in SearchFilterLogic
            wrapperDelegate.put(item.getIdentifier(), wrappedView);
            id2PosMap.put(item.getIdentifier(), position);
            position++;
        }
    }

    @NonNull
    private TextView createViewItem() {
        final TextView view = new TextView(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(
                DeviceUtils.dpToPx(8, context),
                DeviceUtils.dpToPx(4, context),
                DeviceUtils.dpToPx(8, context),
                DeviceUtils.dpToPx(4, context)
        );
        return view;
    }

    public int getItemPositionForFilterId(final int id) {
        return id2PosMap.get(id);
    }

    @Override
    public boolean isEnabled(final int position) {
        final UiItemWrapperSpinner wrappedView =
                viewWrapperMap.get(position);
        Objects.requireNonNull(wrappedView);
        return wrappedView.isEnabled();
    }

    private static class UiItemWrapperSpinner
            extends BaseItemWrapper {
        @NonNull
        private final Spinner spinner;

        /**
         * We have to store the visibility of the view and if it is enabled.
         * <p>
         * Reason: the Spinner adapter reuses {@link View} elements through the parameter
         * convertView in {@link SearchFilterDialogSpinnerAdapter#getView(int, View, ViewGroup)}
         * -> this is the Android Adapter's time saving characteristic to rather reuse
         * than to recreate a {@link View}.
         * -> so we reuse what Android gives us in above mentioned method.
         */
        private int visibility;
        private boolean enabled;

        UiItemWrapperSpinner(@NonNull final FilterItem item,
                             final int initialVisibility,
                             final boolean isInitialEnabled,
                             @NonNull final Spinner spinner) {
            super(item);
            this.spinner = spinner;

            this.visibility = initialVisibility;
            this.enabled = isInitialEnabled;
        }

        @Override
        public void setVisible(final boolean visible) {
            if (visible) {
                visibility = View.VISIBLE;
            } else {
                visibility = View.GONE;
            }
        }

        @Override
        public boolean isChecked() {
            return spinner.getSelectedItem() == item;
        }

        @Override
        public void setChecked(final boolean checked) {
            if (super.getItemId() != FilterContainer.ITEM_IDENTIFIER_UNKNOWN) {
                final SearchFilterDialogSpinnerAdapter adapter =
                        (SearchFilterDialogSpinnerAdapter) spinner.getAdapter();
                spinner.setSelection(adapter.getItemPositionForFilterId(super.getItemId()));
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public int getVisibility() {
            return visibility;
        }

        public void setVisibility(final int visibility) {
            this.visibility = visibility;
        }
    }
}
