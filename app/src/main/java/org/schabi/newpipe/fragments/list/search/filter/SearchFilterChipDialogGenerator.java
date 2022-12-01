// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import com.google.android.material.chip.ChipGroup;

import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.util.DeviceUtils;

import androidx.annotation.NonNull;

public class SearchFilterChipDialogGenerator extends SearchFilterDialogGenerator {

    public SearchFilterChipDialogGenerator(
            @NonNull final SearchFilterLogic logic,
            @NonNull final ViewGroup root,
            @NonNull final Context context) {
        super(logic, root, context);
    }

    @Override
    protected void createFilterGroup(@NonNull final FilterGroup filterGroup,
                                     @NonNull final UiWrapperMapDelegate wrapperDelegate,
                                     @NonNull final UiSelectorDelegate selectorDelegate) {
        final boolean doSpanDataOverMultipleCells = true;
        final UiItemWrapperViews viewsWrapper = new UiItemWrapperViews(
                filterGroup.getIdentifier());

        if (filterGroup.getNameId() != null) {
            final GridLayout.LayoutParams layoutParams =
                    clipFreeRightColumnLayoutParams(doSpanDataOverMultipleCells);
            final TextView filterLabel = createFilterLabel(filterGroup, layoutParams);
            globalLayout.addView(filterLabel);
            viewsWrapper.add(filterLabel);
        } else if (doWeNeedASeparatorView()) {
            final SeparatorLineView separatorLineView = createSeparatorLine();
            globalLayout.addView(separatorLineView);
            viewsWrapper.add(separatorLineView);
        }

        final ChipGroup chipGroup = new ChipGroup(context);
        chipGroup.setLayoutParams(
                setDefaultMarginInDp(clipFreeRightColumnLayoutParams(doSpanDataOverMultipleCells),
                        8, 2, 4, 2));
        chipGroup.setSingleLine(false);
        chipGroup.setSingleSelection(filterGroup.isOnlyOneCheckable());

        createUiChipElementsForFilterGroupItems(
                filterGroup, wrapperDelegate, selectorDelegate, chipGroup);


        wrapperDelegate.put(filterGroup.getIdentifier(), viewsWrapper);
        globalLayout.addView(chipGroup);
        viewsWrapper.add(chipGroup);
    }

    private boolean doWeNeedASeparatorView() {
        // if 0 than there is nothing to separate
        if (globalLayout.getChildCount() == 0) {
            return false;
        }
        final View lastView = globalLayout.getChildAt(globalLayout.getChildCount() - 1);
        return !(lastView instanceof SeparatorLineView);
    }

    private ViewGroup.MarginLayoutParams setDefaultMarginInDp(
            @NonNull final ViewGroup.MarginLayoutParams layoutParams,
            final int left, final int top, final int right, final int bottom) {
        layoutParams.setMargins(
                DeviceUtils.dpToPx(left, context),
                DeviceUtils.dpToPx(top, context),
                DeviceUtils.dpToPx(right, context),
                DeviceUtils.dpToPx(bottom, context)
        );
        return layoutParams;
    }

}
