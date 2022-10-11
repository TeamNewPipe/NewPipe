// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SearchFilterDialogGenerator extends BaseSearchFilterUiDialogGenerator {
    private static final int CHIP_GROUP_ELEMENTS_THRESHOLD = 2;
    private static final int CHIP_MIN_TOUCH_TARGET_SIZE_DP = 40;
    protected final GridLayout globalLayout;

    public SearchFilterDialogGenerator(
            @NonNull final SearchFilterLogic logic,
            @NonNull final ViewGroup root,
            @NonNull final Context context) {
        super(logic, context);
        this.globalLayout = createGridLayout();
        root.addView(globalLayout);
    }

    @Override
    protected void createTitle(@NonNull final String name,
                               @NonNull final List<View> titleViewElements) {
        final TextView titleView = createTitleText(name);
        final View separatorLine = createSeparatorLine();
        final View separatorLine2 = createSeparatorLine();

        globalLayout.addView(separatorLine);
        globalLayout.addView(titleView);
        globalLayout.addView(separatorLine2);

        titleViewElements.add(titleView);
        titleViewElements.add(separatorLine);
        titleViewElements.add(separatorLine2);
    }

    @Override
    protected void createFilterGroup(@NonNull final FilterGroup filterGroup,
                                     @NonNull final UiWrapperMapDelegate wrapperDelegate,
                                     @NonNull final UiSelectorDelegate selectorDelegate) {
        final GridLayout.LayoutParams layoutParams = getLayoutParamsViews();
        boolean doSpanDataOverMultipleCells = false;
        final UiItemWrapperViews viewsWrapper = new UiItemWrapperViews(
                filterGroup.getIdentifier());

        final TextView filterLabel;
        if (filterGroup.getNameId() != null) {
            filterLabel = createFilterLabel(filterGroup, layoutParams);
            viewsWrapper.add(filterLabel);
        } else {
            filterLabel = null;
            doSpanDataOverMultipleCells = true;
        }

        if (filterGroup.isOnlyOneCheckable()) {
            if (filterLabel != null) {
                globalLayout.addView(filterLabel);
            }

            final Spinner filterDataSpinner = new Spinner(context, Spinner.MODE_DROPDOWN);

            final GridLayout.LayoutParams spinnerLp =
                    clipFreeRightColumnLayoutParams(doSpanDataOverMultipleCells);
            setDefaultMargin(spinnerLp);
            filterDataSpinner.setLayoutParams(spinnerLp);
            setZeroPadding(filterDataSpinner);

            createUiElementsForSingleSelectableItemsFilterGroup(
                    filterGroup, wrapperDelegate, selectorDelegate, filterDataSpinner);

            viewsWrapper.add(filterDataSpinner);
            globalLayout.addView(filterDataSpinner);

        } else { // multiple items in FilterGroup selectable
            final ChipGroup chipGroup = new ChipGroup(context);
            doSpanDataOverMultipleCells = chooseParentViewForFilterLabelAndAdd(
                    filterGroup, doSpanDataOverMultipleCells, filterLabel, chipGroup);

            viewsWrapper.add(chipGroup);
            globalLayout.addView(chipGroup);
            chipGroup.setLayoutParams(
                    clipFreeRightColumnLayoutParams(doSpanDataOverMultipleCells));
            chipGroup.setSingleLine(false);

            createUiChipElementsForFilterGroupItems(
                    filterGroup, wrapperDelegate, selectorDelegate, chipGroup);
        }

        wrapperDelegate.put(filterGroup.getIdentifier(), viewsWrapper);
    }

    @NonNull
    protected TextView createFilterLabel(@NonNull final FilterGroup filterGroup,
                                         @NonNull final GridLayout.LayoutParams layoutParams) {
        final TextView filterLabel;
        filterLabel = new TextView(context);

        filterLabel.setId(filterGroup.getIdentifier());
        filterLabel.setText(
                ServiceHelper.getTranslatedFilterString(filterGroup.getNameId(), context));
        filterLabel.setGravity(Gravity.CENTER_VERTICAL);
        setDefaultMargin(layoutParams);
        setZeroPadding(filterLabel);

        filterLabel.setLayoutParams(layoutParams);
        return filterLabel;
    }

    private boolean chooseParentViewForFilterLabelAndAdd(
            @NonNull final FilterGroup filterGroup,
            final boolean doSpanDataOverMultipleCells,
            @Nullable final TextView filterLabel,
            @NonNull final ChipGroup possibleParentView) {

        boolean spanOverMultipleCells = doSpanDataOverMultipleCells;
        if (filterLabel != null) {
            // If we have more than CHIP_GROUP_ELEMENTS_THRESHOLD elements to be
            // displayed as Chips add its filterLabel as first element to ChipGroup.
            // Now the ChipGroup can be spanned over all the cells to use
            // the space better.
            if (filterGroup.getFilterItems().size() > CHIP_GROUP_ELEMENTS_THRESHOLD) {
                possibleParentView.addView(filterLabel);
                spanOverMultipleCells = true;
            } else {
                globalLayout.addView(filterLabel);
            }
        }
        return spanOverMultipleCells;
    }

    private void createUiElementsForSingleSelectableItemsFilterGroup(
            @NonNull final FilterGroup filterGroup,
            @NonNull final UiWrapperMapDelegate wrapperDelegate,
            @NonNull final UiSelectorDelegate selectorDelegate,
            @NonNull final Spinner filterDataSpinner) {
        filterDataSpinner.setAdapter(new SearchFilterDialogSpinnerAdapter(
                context, filterGroup, wrapperDelegate, filterDataSpinner));

        final AdapterView.OnItemSelectedListener listener;
        listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                if (view != null) {
                    selectorDelegate.selectFilter(view.getId());
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // we are only interested onItemSelected() -> no implementation here
            }
        };

        filterDataSpinner.setOnItemSelectedListener(listener);
    }

    protected void createUiChipElementsForFilterGroupItems(
            @NonNull final FilterGroup filterGroup,
            @NonNull final UiWrapperMapDelegate wrapperDelegate,
            @NonNull final UiSelectorDelegate selectorDelegate,
            @NonNull final ChipGroup chipGroup) {
        for (final FilterItem item : filterGroup.getFilterItems()) {

            if (item instanceof InjectFilterItem.DividerItem) {
                final InjectFilterItem.DividerItem dividerItem =
                        (InjectFilterItem.DividerItem) item;

                // For the width MATCH_PARENT is necessary as this allows the
                // dividerLabel to fill one row of ChipGroup exclusively
                final ChipGroup.LayoutParams layoutParams = new ChipGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                final TextView dividerLabel = createDividerLabel(dividerItem, layoutParams);
                chipGroup.addView(dividerLabel);
            } else {
                final Chip chip = createChipView(chipGroup, item);

                final View.OnClickListener listener;
                listener = view -> selectorDelegate.selectFilter(view.getId());
                chip.setOnClickListener(listener);

                chipGroup.addView(chip);
                wrapperDelegate.put(item.getIdentifier(),
                        new UiItemWrapperChip(item, chip, chipGroup));
            }
        }
    }

    @NonNull
    private Chip createChipView(@NonNull final ChipGroup chipGroup,
                                @NonNull final FilterItem item) {
        final Chip chip = (Chip) LayoutInflater.from(context).inflate(
                R.layout.chip_search_filter, chipGroup, false);
        chip.ensureAccessibleTouchTarget(
                DeviceUtils.dpToPx(CHIP_MIN_TOUCH_TARGET_SIZE_DP, context));
        chip.setText(ServiceHelper.getTranslatedFilterString(item.getNameId(), context));
        chip.setId(item.getIdentifier());
        chip.setCheckable(true);
        return chip;
    }

    @NonNull
    private TextView createDividerLabel(
            @NonNull final InjectFilterItem.DividerItem dividerItem,
            @NonNull final ViewGroup.MarginLayoutParams layoutParams) {
        final TextView dividerLabel;
        dividerLabel = new TextView(context);
        dividerLabel.setEnabled(true);

        dividerLabel.setGravity(Gravity.CENTER_VERTICAL);
        setDefaultMargin(layoutParams);
        dividerLabel.setLayoutParams(layoutParams);
        final String menuDividerTitle =
                context.getString(dividerItem.getStringResId());
        dividerLabel.setText(menuDividerTitle);
        return dividerLabel;
    }

    @NonNull
    protected SeparatorLineView createSeparatorLine() {
        return createSeparatorLine(clipFreeRightColumnLayoutParams(true));
    }

    @NonNull
    private TextView createTitleText(final String name) {
        final TextView title = createTitleText(name,
                clipFreeRightColumnLayoutParams(true));
        title.setGravity(Gravity.CENTER);
        return title;
    }

    @NonNull
    private GridLayout createGridLayout() {
        final GridLayout layout = new GridLayout(context);

        layout.setColumnCount(2);

        final GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        setDefaultMargin(layoutParams);
        layout.setLayoutParams(layoutParams);

        return layout;
    }

    @NonNull
    protected GridLayout.LayoutParams clipFreeRightColumnLayoutParams(final boolean doColumnSpan) {
        final GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
        // https://stackoverflow.com/questions/37744672/gridlayout-children-are-being-clipped
        layoutParams.width = 0;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.setGravity(Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL);
        setDefaultMargin(layoutParams);

        if (doColumnSpan) {
            layoutParams.columnSpec = GridLayout.spec(0, 2, 1.0f);
        }

        return layoutParams;
    }

    @NonNull
    private GridLayout.LayoutParams getLayoutParamsViews() {
        final GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
        layoutParams.setGravity(Gravity.CENTER_VERTICAL);
        setDefaultMargin(layoutParams);
        return layoutParams;
    }

    @NonNull
    protected ViewGroup.MarginLayoutParams setDefaultMargin(
            @NonNull final ViewGroup.MarginLayoutParams layoutParams) {
        layoutParams.setMargins(
                DeviceUtils.dpToPx(4, context),
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(4, context),
                DeviceUtils.dpToPx(2, context)
        );
        return layoutParams;
    }

    @NonNull
    protected View setZeroPadding(@NonNull final View view) {
        view.setPadding(0, 0, 0, 0);
        return view;
    }

    public static class UiItemWrapperChip extends BaseUiItemWrapper {

        @NonNull
        private final ChipGroup chipGroup;

        public UiItemWrapperChip(@NonNull final FilterItem item,
                                 @NonNull final View view,
                                 @NonNull final ChipGroup chipGroup) {
            super(item, view);
            this.chipGroup = chipGroup;
        }

        @Override
        public boolean isChecked() {
            return ((Chip) view).isChecked();
        }

        @Override
        public void setChecked(final boolean checked) {
            ((Chip) view).setChecked(checked);

            if (checked) {
                chipGroup.check(view.getId());
            }
        }
    }
}
