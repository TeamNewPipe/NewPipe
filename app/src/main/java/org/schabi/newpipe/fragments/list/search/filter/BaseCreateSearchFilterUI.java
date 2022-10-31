// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import static org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic.ICreateUiForFiltersWorker;

/**
 * Common base for the {@link SearchFilterDialogGenerator} and
 * {@link SearchFilterOptionMenuAlikeDialogGenerator}'s
 * {@link ICreateUiForFiltersWorker} implementation.
 */
public abstract class BaseCreateSearchFilterUI
        implements ICreateUiForFiltersWorker {

    @NonNull
    protected final BaseSearchFilterUiDialogGenerator dialogGenBase;
    @NonNull
    protected final Context context;
    protected final List<View> titleViewElements = new ArrayList<>();
    protected final SearchFilterLogic logic;
    protected int titleResId;

    protected BaseCreateSearchFilterUI(
            @NonNull final BaseSearchFilterUiDialogGenerator dialogGenBase,
            @NonNull final SearchFilterLogic logic,
            @NonNull final Context context,
            final int titleResId) {
        this.dialogGenBase = dialogGenBase;
        this.logic = logic;
        this.context = context;
        this.titleResId = titleResId;
    }

    @Override
    public void createFilterItem(@NonNull final FilterItem filterItem,
                                 @NonNull final FilterGroup filterGroup) {
        // no implementation here all creation stuff is done in createFilterGroupBeforeItems
    }

    @Override
    public void createFilterGroupAfterItems(@NonNull final FilterGroup filterGroup) {
        // no implementation here all creation stuff is done in createFilterGroupBeforeItems
    }

    @Override
    public void finish() {
        // no implementation here all creation stuff is done in createFilterGroupBeforeItems
    }

    /**
     * This method is used to control the visibility of the title 'sort filter' if the
     * chosen content filter has no sort filters.
     *
     * @param areFiltersVisible true if filter visible
     */
    @Override
    public void filtersVisible(final boolean areFiltersVisible) {
        final int visibility = areFiltersVisible ? View.VISIBLE : View.GONE;
        for (final View view : titleViewElements) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    public static class CreateContentFilterUI extends CreateSortFilterUI {

        public CreateContentFilterUI(
                @NonNull final BaseSearchFilterUiDialogGenerator dialogGenBase,
                @NonNull final Context context,
                @NonNull final SearchFilterLogic logic) {
            super(dialogGenBase, context, logic);
            this.titleResId = R.string.filter_search_content_filters;
        }

        @Override
        public void createFilterGroupBeforeItems(
                @NonNull final FilterGroup filterGroup) {
            dialogGenBase.createFilterGroup(filterGroup,
                    logic::addContentFilterUiWrapperToItemMap,
                    logic::selectContentFilter);
        }

        @Override
        public void filtersVisible(final boolean areFiltersVisible) {
            // no implementation here. As content filters have to be always visible
        }
    }

    public static class CreateSortFilterUI extends BaseCreateSearchFilterUI {

        public CreateSortFilterUI(
                @NonNull final BaseSearchFilterUiDialogGenerator dialogGenBase,
                @NonNull final Context context,
                @NonNull final SearchFilterLogic logic) {
            super(dialogGenBase, logic, context, R.string.filter_search_sort_filters);
        }

        @Override
        public void prepare() {
            dialogGenBase.createTitle(context.getString(titleResId), titleViewElements);
        }

        @Override
        public void createFilterGroupBeforeItems(@NonNull final FilterGroup filterGroup) {
            dialogGenBase.createFilterGroup(filterGroup,
                    logic::addSortFilterUiWrapperToItemMap,
                    logic::selectSortFilter);
        }
    }
}
