// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static org.schabi.newpipe.fragments.list.search.filter.InjectFilterItem.DividerItem;

public class SearchFilterOptionMenuAlikeDialogGenerator extends BaseSearchFilterUiDialogGenerator {
    private static final Integer NO_RESIZE_VIEW_TAG = 1;
    private static final float FONT_SIZE_SELECTABLE_VIEW_ITEMS_IN_DIP = 18f;
    private static final int VIEW_ITEMS_MIN_WIDTH_IN_DIP = 168;
    private final LinearLayout globalLayout;

    public SearchFilterOptionMenuAlikeDialogGenerator(
            @NonNull final SearchFilterLogic logic,
            @NonNull final ViewGroup root,
            @NonNull final Context context) {
        super(logic, context);
        this.globalLayout = createLinearLayout();
        root.addView(globalLayout);
    }

    @Override
    protected void doMeasurementsIfNeeded() {
        measureWidthOfChildrenAndResizeToWidest();
    }

    /**
     * Resize all width of {@link #globalLayout} children without tag {@link #NO_RESIZE_VIEW_TAG}.
     * <p>
     * Initially this method was only used to resize the width of separator line
     * views created by {@link #createSeparatorLine()}. But now also the views
     * the user will interact with are set to the widest child.
     * <p>
     * Reasons:
     * 1. Separator lines should be as wide as the widest UI element but this
     * can only be determined on runtime
     * 2. Other view elements more specific checkable/selectable should also
     * expand their width over the complete dialog width to be easier to select
     */
    private void measureWidthOfChildrenAndResizeToWidest() {
        logic.showAllAvailableSortFilters();

        // initialize width with a passable default width
        int widestViewInPx = DeviceUtils.dpToPx(VIEW_ITEMS_MIN_WIDTH_IN_DIP, context);
        final int noOfChildren = globalLayout.getChildCount();

        for (int x = 0; x < noOfChildren; x++) {
            final View childView = globalLayout.getChildAt(x);
            childView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            final int width = childView.getMeasuredWidth();
            if (width > widestViewInPx) {
                widestViewInPx = width;
            }
        }

        for (int x = 0; x < noOfChildren; x++) {
            final View childView = globalLayout.getChildAt(x);

            if (childView.getTag() != NO_RESIZE_VIEW_TAG) {
                final ViewGroup.LayoutParams layoutParams = childView.getLayoutParams();
                layoutParams.width = widestViewInPx;
                childView.setLayoutParams(layoutParams);
            }
        }
    }

    @Override
    protected void createTitle(@NonNull final String name,
                               @NonNull final List<View> titleViewElements) {
        final TextView titleView = createTitleText(name);
        titleView.setTag(NO_RESIZE_VIEW_TAG);
        final View separatorLine = createSeparatorLine();
        final View separatorLine2 = createSeparatorLine();
        final View separatorLine3 = createSeparatorLine();

        globalLayout.addView(separatorLine);
        globalLayout.addView(separatorLine2);
        globalLayout.addView(titleView);
        globalLayout.addView(separatorLine3);

        titleViewElements.add(titleView);
        titleViewElements.add(separatorLine);
        titleViewElements.add(separatorLine2);
        titleViewElements.add(separatorLine3);
    }

    @Override
    protected void createFilterGroup(@NonNull final FilterGroup filterGroup,
                                     @NonNull final UiWrapperMapDelegate wrapperDelegate,
                                     @NonNull final UiSelectorDelegate selectorDelegate) {
        final UiItemWrapperViews viewsWrapper = new UiItemWrapperViews(
                filterGroup.getIdentifier());

        final View separatorLine = createSeparatorLine();
        globalLayout.addView(separatorLine);
        viewsWrapper.add(separatorLine);

        if (filterGroup.getNameId() != null) {
            final TextView filterLabel =
                    createFilterGroupLabel(filterGroup, getLayoutParamsLabelLeft());
            globalLayout.addView(filterLabel);
            viewsWrapper.add(filterLabel);
        }

        if (filterGroup.isOnlyOneCheckable()) {

            final RadioGroup radioGroup = new RadioGroup(context);
            radioGroup.setLayoutParams(getLayoutParamsViews());

            createUiElementsForSingleSelectableItemsFilterGroup(
                    filterGroup, wrapperDelegate, selectorDelegate, radioGroup);

            globalLayout.addView(radioGroup);
            viewsWrapper.add(radioGroup);

        } else { // multiple items in FilterGroup selectable
            createUiElementsForMultipleSelectableItemsFilterGroup(
                    filterGroup, wrapperDelegate, selectorDelegate);
        }

        wrapperDelegate.put(filterGroup.getIdentifier(), viewsWrapper);
    }

    private void createUiElementsForSingleSelectableItemsFilterGroup(
            @NonNull final FilterGroup filterGroup,
            @NonNull final UiWrapperMapDelegate wrapperDelegate,
            @NonNull final UiSelectorDelegate selectorDelegate,
            @NonNull final RadioGroup radioGroup) {
        for (final FilterItem item : filterGroup.getFilterItems()) {

            final View view;
            if (item instanceof DividerItem) {
                view = createDividerTextView(item, getLayoutParamsViews());
            } else {
                view = createViewItemRadio(item, getLayoutParamsViews());

                wrapperDelegate.put(item.getIdentifier(),
                        new UiItemWrapperCheckBoxAndRadioButton(
                                item, view, radioGroup));

                final View.OnClickListener listener = v -> {
                    if (v != null) {
                        selectorDelegate.selectFilter(v.getId());
                    }
                };
                view.setOnClickListener(listener);
            }
            radioGroup.addView(view);
        }
    }

    private void createUiElementsForMultipleSelectableItemsFilterGroup(
            @NonNull final FilterGroup filterGroup,
            @NonNull final UiWrapperMapDelegate wrapperDelegate,
            @NonNull final UiSelectorDelegate selectorDelegate) {
        for (final FilterItem item : filterGroup.getFilterItems()) {
            final View view;
            if (item instanceof DividerItem) {
                view = createDividerTextView(item, getLayoutParamsViews());
            } else {
                final CheckBox checkBox = createCheckBox(item, getLayoutParamsViews());

                wrapperDelegate.put(item.getIdentifier(),
                        new UiItemWrapperCheckBoxAndRadioButton(
                                item, checkBox, null));

                final View.OnClickListener listener = v -> {
                    if (v != null) {
                        selectorDelegate.selectFilter(v.getId());
                    }
                };
                checkBox.setOnClickListener(listener);

                view = checkBox;
            }
            globalLayout.addView(view);
        }
    }

    @NonNull
    private LinearLayout createLinearLayout() {
        final LinearLayout linearLayout = new LinearLayout(context);

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(1, 1);
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.setMargins(
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context));
        linearLayout.setLayoutParams(layoutParams);

        return linearLayout;
    }

    @NonNull
    private LinearLayout.LayoutParams getLayoutForSeparatorLine() {
        final LinearLayout.LayoutParams layoutParams = getLayoutParamsLabelLeft();
        layoutParams.width = 0;
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        return layoutParams;
    }

    @NonNull
    private View createSeparatorLine() {
        return createSeparatorLine(getLayoutForSeparatorLine());
    }

    @NonNull
    private TextView createTitleText(final String name) {
        final LinearLayout.LayoutParams layoutParams = getLayoutParamsLabelLeft();
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        final TextView title = createTitleText(name, layoutParams);
        setPadding(title, 5);
        return title;
    }

    @NonNull
    private View setPadding(@NonNull final View view, final int sizeInDip) {
        final int sizeInPx = DeviceUtils.dpToPx(sizeInDip, context);
        view.setPadding(
                sizeInPx,
                sizeInPx,
                sizeInPx,
                sizeInPx);
        return view;
    }

    @NonNull
    private TextView createFilterGroupLabel(@NonNull final FilterGroup filterGroup,
                                            @NonNull final ViewGroup.LayoutParams layoutParams) {
        final TextView filterLabel = new TextView(context);
        filterLabel.setId(filterGroup.getIdentifier());
        filterLabel.setText(ServiceHelper
                .getTranslatedFilterString(filterGroup.getNameId(), context));
        filterLabel.setGravity(Gravity.TOP);
        // resizing not needed as view is not selectable
        filterLabel.setTag(NO_RESIZE_VIEW_TAG);
        filterLabel.setLayoutParams(layoutParams);
        return filterLabel;
    }

    @NonNull
    private CheckBox createCheckBox(@NonNull final FilterItem item,
                                    @NonNull final ViewGroup.LayoutParams layoutParams) {
        final CheckBox checkBox = new CheckBox(context);
        checkBox.setLayoutParams(layoutParams);
        checkBox.setText(ServiceHelper.getTranslatedFilterString(
                item.getNameId(), context));
        checkBox.setId(item.getIdentifier());
        checkBox.setTextSize(COMPLEX_UNIT_DIP, FONT_SIZE_SELECTABLE_VIEW_ITEMS_IN_DIP);
        return checkBox;
    }

    @NonNull
    private TextView createDividerTextView(@NonNull final FilterItem item,
                                           @NonNull final ViewGroup.LayoutParams layoutParams) {
        final DividerItem dividerItem = (DividerItem) item;
        final TextView view = new TextView(context);
        view.setEnabled(true);
        final String menuDividerTitle =
                context.getString(dividerItem.getStringResId());
        view.setText(menuDividerTitle);
        view.setGravity(Gravity.TOP);
        view.setLayoutParams(layoutParams);
        return view;
    }

    @NonNull
    private RadioButton createViewItemRadio(@NonNull final FilterItem item,
                                            @NonNull final ViewGroup.LayoutParams layoutParams) {
        final RadioButton view = new RadioButton(context);
        view.setId(item.getIdentifier());
        view.setText(ServiceHelper.getTranslatedFilterString(item.getNameId(), context));
        view.setLayoutParams(layoutParams);
        view.setTextSize(COMPLEX_UNIT_DIP, FONT_SIZE_SELECTABLE_VIEW_ITEMS_IN_DIP);
        return view;
    }

    @NonNull
    private LinearLayout.LayoutParams getLayoutParamsViews() {
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(
                DeviceUtils.dpToPx(4, context),
                DeviceUtils.dpToPx(8, context),
                DeviceUtils.dpToPx(4, context),
                DeviceUtils.dpToPx(8, context));
        return layoutParams;
    }

    @NonNull
    private LinearLayout.LayoutParams getLayoutParamsLabelLeft() {
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context),
                DeviceUtils.dpToPx(2, context));
        return layoutParams;
    }

    private static final class UiItemWrapperCheckBoxAndRadioButton
            extends BaseUiItemWrapper {

        @Nullable
        private final View group;

        private UiItemWrapperCheckBoxAndRadioButton(@NonNull final FilterItem item,
                                                    @NonNull final View view,
                                                    @Nullable final View group) {
            super(item, view);
            this.group = group;
        }

        @Override
        public boolean isChecked() {
            if (view instanceof RadioButton) {
                return ((RadioButton) view).isChecked();
            } else if (view instanceof CheckBox) {
                return ((CheckBox) view).isChecked();
            } else {
                return view.isSelected();
            }
        }

        @Override
        public void setChecked(final boolean checked) {
            if (checked && group instanceof RadioGroup) {
                ((RadioGroup) group).check(view.getId());
            } else if (view instanceof CheckBox) {
                ((CheckBox) view).setChecked(checked);
            } else {
                view.setSelected(checked);
            }
        }
    }
}
