// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.extractor.search.filter.FilterGroup;

import java.util.List;

import androidx.annotation.NonNull;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic.ICreateUiForFiltersWorker;

public abstract class BaseSearchFilterUiDialogGenerator extends BaseSearchFilterUiGenerator {
    private static final float FONT_SIZE_TITLE_ITEMS_IN_DIP = 20f;

    protected BaseSearchFilterUiDialogGenerator(
            @NonNull final SearchFilterLogic logic,
            @NonNull final Context context) {
        super(logic, context);
    }

    protected abstract void createTitle(@NonNull String name,
                                        @NonNull List<View> titleViewElements);

    protected abstract void createFilterGroup(@NonNull FilterGroup filterGroup,
                                              @NonNull UiWrapperMapDelegate wrapperDelegate,
                                              @NonNull UiSelectorDelegate selectorDelegate);

    @Override
    protected ICreateUiForFiltersWorker createContentFilterWorker() {
        return new BaseCreateSearchFilterUI.CreateContentFilterUI(this, context, logic);
    }

    @Override
    protected ICreateUiForFiltersWorker createSortFilterWorker() {
        return new BaseCreateSearchFilterUI.CreateSortFilterUI(this, context, logic);
    }

    /**
     * Create a View that acts as a separator between two other {@link View}-Elements.
     *
     * @param layoutParams this layout will be modified to have the height of 1 -> to have a
     *                     the actual separator line.
     * @return the created {@link SeparatorLineView}
     */
    @NonNull
    protected SeparatorLineView createSeparatorLine(
            @NonNull final ViewGroup.LayoutParams layoutParams) {
        final SeparatorLineView separatorLine = new SeparatorLineView(context);
        separatorLine.setBackgroundColor(getSeparatorLineColorFromTheme());
        layoutParams.height = 1; // always set the separator to the height of 1
        separatorLine.setLayoutParams(layoutParams);
        return separatorLine;
    }

    @NonNull
    protected TextView createTitleText(@NonNull final String name,
                                       @NonNull final ViewGroup.LayoutParams layoutParams) {
        final TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(COMPLEX_UNIT_DIP, FONT_SIZE_TITLE_ITEMS_IN_DIP);
        title.setLayoutParams(layoutParams);
        return title;
    }

    /**
     * A special view to separate two other {@link View}s.
     * <p>
     * class only needed to distinct this special view from other View based views.
     * (eg. instanceof)
     */
    protected static final class SeparatorLineView extends View {

        private SeparatorLineView(@NonNull final Context context) {
            super(context);
        }
    }
}
