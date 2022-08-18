// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import android.content.Context;
import android.util.TypedValue;

import org.schabi.newpipe.R;

import androidx.annotation.NonNull;

import static org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic.ICreateUiForFiltersWorker;
import static org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic.IUiItemWrapper;

/**
 * The base class to implement the search filter UI for content
 * and sort filter dialogs eg. {@link SearchFilterDialogGenerator}
 * or {@link SearchFilterOptionMenuAlikeDialogGenerator}.
 */
public abstract class BaseSearchFilterUiGenerator {
    protected final ICreateUiForFiltersWorker contentFilterWorker;
    protected final ICreateUiForFiltersWorker sortFilterWorker;
    protected final Context context;
    protected final SearchFilterLogic logic;

    protected BaseSearchFilterUiGenerator(
            @NonNull final SearchFilterLogic logic,
            @NonNull final Context context) {
        this.context = context;
        this.logic = logic;
        this.contentFilterWorker = createContentFilterWorker();
        this.sortFilterWorker = createSortFilterWorker();
    }

    /**
     * {@link ICreateUiForFiltersWorker}.
     *
     * @return the class that implements the UI for the content filters.
     */
    protected abstract ICreateUiForFiltersWorker createContentFilterWorker();

    /**
     * {@link ICreateUiForFiltersWorker}.
     *
     * @return the class that implements the UI for the sort filters.
     */
    protected abstract ICreateUiForFiltersWorker createSortFilterWorker();

    protected int getSeparatorLineColorFromTheme() {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorAccent, value, true);
        return value.data;
    }

    /**
     * Create the complete UI for the search filter dialog and make sure the initial
     * visibility of the UI elements is done.
     */
    public void createSearchUI() {
        logic.initContentFiltersUi(contentFilterWorker);
        logic.initSortFiltersUi(sortFilterWorker);
        doMeasurementsIfNeeded();
        // make sure that only sort filters relevant to the selected content filter are shown
        logic.showSortFilterContainerUI();
    }

    protected void doMeasurementsIfNeeded() {
        // nothing to measure here, if you want to measure something override this method
    }

    /**
     * Helper interface used as 'function pointer'.
     */
    protected interface UiWrapperMapDelegate {
        void put(int identifier, IUiItemWrapper menuItemUiWrapper);
    }

    /**
     * Helper interface used as 'function pointer'.
     */
    protected interface UiSelectorDelegate {
        void selectFilter(int identifier);
    }
}
