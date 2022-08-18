// Created by evermind-zz 2022, licensed GNU GPL version 3 or later

package org.schabi.newpipe.fragments.list.search.filter;

import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterContainer;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import static org.schabi.newpipe.extractor.search.filter.FilterContainer.ITEM_IDENTIFIER_UNKNOWN;

/**
 * This class handles all the user interaction with the content and sort filters
 * of NewPipeExtractor.
 * <p>
 * It also facilitates the generation of the Ui's according to the implemented
 * {@link ICreateUiForFiltersWorker}'s.
 */
public class SearchFilterLogic {

    /**
     * This list is used to communicate with NewPipeExtractor.
     * It contains only the content filter ids that the user has selected from the UI.
     */
    private final List<FilterItem> userSelectedContentFilters = new ArrayList<>();
    /**
     * This list is used to communicate with NewPipeExtractor.
     * It contains only the sort filter ids that the user has selected from the UI.
     */
    private final List<FilterItem> userSelectedSortFilters = new ArrayList<>();
    private final SearchQueryHandlerFactory searchQHFactory;
    private final ExclusiveGroups contentFilterExclusive = new ExclusiveGroups();
    private final ExclusiveGroups sortFilterExclusive = new ExclusiveGroups();
    private final SparseArrayCompat<IUiItemWrapper> contentFilterIdToUiItemMap =
            new SparseArrayCompat<>();
    private final SparseArrayCompat<IUiItemWrapper> sortFilterIdToUiItemMap =
            new SparseArrayCompat<>();
    private final SparseArrayCompat<FilterContainer> contentFilterFidToSupersetSortFilterMap =
            new SparseArrayCompat<>();
    private Callback callback;
    /**
     * This list is used to store via Icepick and eventual store as preset
     * It contains all the content filter ids that the user has selected. It
     * contains the same ids than {@link #userSelectedContentFilters}
     */
    private List<Integer> selectedContentFilters = new ArrayList<>();
    /**
     * This list is used to store via Icepick and eventual store as preset
     * It contains all the sort filter ids that the user has selected and also
     * default id of none visible but selected sort filters.
     * It is a superset to {@link #userSelectedContentFilters}.
     */
    private List<Integer> selectedSortFilters = new ArrayList<>();

    /**
     * Store a reference of the sort filters Ui creator. This is needed
     * as a mechanism to tell if (the sort filter title) should be displayed or not.
     * <p>
     * The work is done via {@link ICreateUiForFiltersWorker#filtersVisible(boolean)}
     */
    private ICreateUiForFiltersWorker uiSortFilterWorker;


    private SearchFilterLogic(@NonNull final SearchQueryHandlerFactory searchQHFactory,
                              @Nullable final Callback callback) {
        this.searchQHFactory = searchQHFactory;
        this.callback = callback;
        initContentFilters();
        initSortFilters();
    }

    public void setCallback(@Nullable final Callback callback) {
        this.callback = callback;
    }

    public void reset() {
        initContentFilters();
        initSortFilters();
        deselectUiItems(contentFilterIdToUiItemMap);
        deselectUiItems(sortFilterIdToUiItemMap);
        reselectUiItems(selectedContentFilters, contentFilterIdToUiItemMap);
        reselectUiItems(selectedSortFilters, sortFilterIdToUiItemMap);
        showSortFilterContainerUI();
    }

    private void reInitExclusiveFilterIds(@NonNull final List<Integer> selectedFilters,
                                          @NonNull final ExclusiveGroups exclusive) {
        checkIfIdsAreValid(selectedFilters, exclusive);

        for (final int id : selectedFilters) {
            exclusive.ifInExclusiveGroupRemovePreviouslySelectedId(id);
            exclusive.addIdIfBelongsToExclusiveGroup(id);
        }
    }

    public void restorePreviouslySelectedFilters(
            @Nullable final List<Integer> selectedContentFilterList,
            @Nullable final List<Integer> selectedSortFilterList) {
        if (selectedContentFilterList != null && selectedSortFilterList != null
                && !selectedContentFilterList.isEmpty()) {
            reInitExclusiveFilterIds(selectedContentFilterList, contentFilterExclusive);
            reInitExclusiveFilterIds(selectedSortFilterList, sortFilterExclusive);

            this.selectedContentFilters = selectedContentFilterList;
            this.selectedSortFilters = selectedSortFilterList;
        }

        createContentFilterItemListFromIdentifierList();
        createSortFilterItemListFromIdentifiersList();
    }

    private void reselectUiItems(
            @NonNull final List<Integer> selectedFilters,
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap) {
        for (final int id : selectedFilters) {
            final IUiItemWrapper iUiItemWrapper = filterIdToUiItemMap.get(id);
            if (iUiItemWrapper != null) {
                iUiItemWrapper.setChecked(true);
            }
        }
    }

    private void deselectUiItems(
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap) {
        for (int index = 0; index < filterIdToUiItemMap.size(); index++) {
            final IUiItemWrapper iUiItemWrapper = filterIdToUiItemMap.valueAt(index);
            if (iUiItemWrapper != null) {
                iUiItemWrapper.setChecked(false);
            }
        }
    }

    // get copy of internal list
    @NonNull
    public ArrayList<Integer> getSelectedContentFilters() {
        return new ArrayList<>(this.selectedContentFilters);
    }

    // get copy of internal list
    @NonNull
    public ArrayList<Integer> getSelectedSortFilters() {
        return new ArrayList<>(this.selectedSortFilters);
    }

    // get copy of internal list, elements are not copied
    @NonNull
    public List<FilterItem> getSelectedContentFilterItems() {
        return new ArrayList<>(this.userSelectedContentFilters);
    }

    // get copy of internal list, elements are not copied
    @NonNull
    public List<FilterItem> getSelectedSortFiltersItems() {
        return new ArrayList<>(this.userSelectedSortFilters);
    }

    public void initContentFiltersUi(
            @NonNull final ICreateUiForFiltersWorker createUiForFiltersWorker) {
        final FilterContainer filters = searchQHFactory.getAvailableContentFilter();

        if (filters != null && filters.getFilterGroups() != null) {
            initFiltersUi(filters.getFilterGroups(),
                    contentFilterIdToUiItemMap,
                    createUiForFiltersWorker);
        }

        reselectUiItems(selectedContentFilters, contentFilterIdToUiItemMap);
    }

    public void initSortFiltersUi(
            @NonNull final ICreateUiForFiltersWorker createUiForFiltersWorker) {
        final FilterContainer filters = searchQHFactory.getAvailableContentFilter();
        final List<FilterGroup> sortGroups = getAllSortFilterGroups(filters);
        uiSortFilterWorker = createUiForFiltersWorker;

        initFiltersUi(sortGroups,
                sortFilterIdToUiItemMap,
                createUiForFiltersWorker);

        reselectUiItems(selectedSortFilters, sortFilterIdToUiItemMap);
    }

    /**
     * Create Ui elements.
     *
     * @param filterGroups             the filter groups that whom a UI should be created
     * @param filterIdToUiItemMap      points to a {@link FilterItem} or {@link FilterGroup}
     *                                 corresponding actual UI element(s). This map will be first
     *                                 called clear() on here.
     * @param createUiForFiltersWorker the implementation how to create the UI.
     */
    private void initFiltersUi(
            @NonNull final List<FilterGroup> filterGroups,
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap,
            @NonNull final ICreateUiForFiltersWorker createUiForFiltersWorker) {

        filterIdToUiItemMap.clear();
        Objects.requireNonNull(createUiForFiltersWorker);
        createUiForFiltersWorker.prepare();
        for (final FilterGroup filterGroup : filterGroups) {
            createUiForFiltersWorker.createFilterGroupBeforeItems(filterGroup);
            for (final FilterItem filterItem : filterGroup.getFilterItems()) {
                createUiForFiltersWorker.createFilterItem(filterItem, filterGroup);
            }
            createUiForFiltersWorker.createFilterGroupAfterItems(filterGroup);
        }
        createUiForFiltersWorker.finish();
    }

    /**
     * Init the content filter logical states.
     * <p>
     * - create list with default id that will be preselected
     * - create exclusivity lists for exclusive groups
     * {@link ExclusiveGroups#filterIdToGroupIdMap} and
     * {@link ExclusiveGroups#exclusiveGroupsIdSet}
     * - check if {@link #selectedContentFilters} are valid ids
     *
     * @param filterGroups               content or sort filter {@link FilterGroup} array
     * @param exclusive                  corresponding exclusive object (either for content
     *                                   or sort) filter array
     * @param selectedFilters            corresponding selected filter ids
     * @param fidToSupersetSortFilterMap null possible, only for content filters relevant
     */
    private void initFilters(
            @NonNull final List<FilterGroup> filterGroups,
            @NonNull final ExclusiveGroups exclusive,
            @NonNull final List<Integer> selectedFilters,
            @Nullable final SparseArrayCompat<FilterContainer> fidToSupersetSortFilterMap) {
        selectedFilters.clear();
        exclusive.clear();

        for (final FilterGroup filterGroup : filterGroups) {
            if (filterGroup.isOnlyOneCheckable()) {
                exclusive.addGroupToExclusiveGroupsMap(filterGroup.getIdentifier());
            }

            // is the default selected filter for this group
            final int defaultId = filterGroup.getDefaultSelectedFilterId();

            for (final FilterItem item : filterGroup.getFilterItems()) {
                if (fidToSupersetSortFilterMap != null) {
                    fidToSupersetSortFilterMap.put(item.getIdentifier(),
                            filterGroup.getAllSortFilters());
                }
                exclusive.putFilterIdToItsGroupId(item.getIdentifier(),
                        filterGroup.getIdentifier());
            }

            if (defaultId != ITEM_IDENTIFIER_UNKNOWN) {
                exclusive.handleIdInExclusiveGroup(defaultId, selectedFilters);
            }
        }

        checkIfIdsAreValid(selectedFilters, exclusive);
    }

    private void checkIfIdsAreValid(@NonNull final List<Integer> selectedFilters,
                                    @NonNull final ExclusiveGroups exclusive) {
        for (final int id : selectedFilters) {
            if (!exclusive.filterIdToGroupIdMapContainsId(id)) {
                throw new RuntimeException("The id " + id + " is invalid");
            }
        }
    }

    private void initContentFilters() {
        final FilterContainer filters = searchQHFactory.getAvailableContentFilter();
        contentFilterFidToSupersetSortFilterMap.clear();

        if (filters != null && filters.getFilterGroups() != null) {
            initFilters(filters.getFilterGroups(),
                    contentFilterExclusive, selectedContentFilters,
                    contentFilterFidToSupersetSortFilterMap);
        }
    }

    private void initSortFilters() {
        final FilterContainer filters = searchQHFactory.getAvailableContentFilter();
        final List<FilterGroup> sortGroups = getAllSortFilterGroups(filters);
        initFilters(sortGroups, sortFilterExclusive, selectedSortFilters, null);
    }

    /**
     * Prepare content filter list with the actual {@link FilterItem}s to send to the library.
     * <p>
     * The list is created through the {@link #userSelectedContentFilters} identifiers list.
     * This identifiers refer to {@link FilterItem}s.
     * <p>
     * {@link #userSelectedContentFilters} will be cleared first!
     */
    private void createContentFilterItemListFromIdentifierList() {
        userSelectedContentFilters.clear();
        final FilterContainer filterContainer = searchQHFactory.getAvailableContentFilter();

        for (final int contentFilterId : selectedContentFilters) {
            final FilterItem contentFilterItem = filterContainer.getFilterItem(contentFilterId);
            if (contentFilterItem != null) {
                userSelectedContentFilters.add(contentFilterItem);
            }
        }
    }

    /**
     * Prepare sort filter list with the actual {@link FilterItem}s to send to the library.
     * <p>
     * The list is created through the {@link #userSelectedSortFilters} identifiers list.
     * This identifiers refer to {@link FilterItem}s.
     * <p>
     * {@link #userSelectedSortFilters} will be cleared first!
     */
    private void createSortFilterItemListFromIdentifiersList() {
        userSelectedSortFilters.clear();
        for (final int sortFilterId : selectedSortFilters) {
            for (final int contentFilterId : selectedContentFilters) {
                final FilterContainer filterContainer =
                        searchQHFactory.getContentFilterSortFilterVariant(contentFilterId);
                if (filterContainer != null) {
                    final FilterItem sortFilterItem = filterContainer.getFilterItem(sortFilterId);
                    if (sortFilterItem != null) {
                        userSelectedSortFilters.add(sortFilterItem);
                    }
                }
            }
        }
    }

    public void showSortFilterContainerUI() {
        showSortFilterIdsContainerUI(selectedContentFilters);
    }

    /**
     * Show only that sort filter UIs that are available for selected content ids.
     *
     * @param contentFilterIds content filter ids list
     */
    private void showSortFilterIdsContainerUI(@NonNull final List<Integer> contentFilterIds) {
        for (final int contentFilterId : contentFilterIds) {
            showSortFilterIdContainerUI(contentFilterId);
        }
    }

    private void notifySortFiltersVisibility() {
        boolean sortFilterVisible = false;
        if (uiSortFilterWorker != null) {
            for (final int contentFilterId : selectedContentFilters) {
                sortFilterVisible = searchQHFactory
                        .getContentFilterSortFilterVariant(contentFilterId) != null;
                if (sortFilterVisible) {
                    break;
                }
            }
            uiSortFilterWorker.filtersVisible(sortFilterVisible);
        }
    }

    /**
     * Show only the sort filters that are available for a given content filter id.
     *
     * @param contentFilterId a content filter id and <b>not</b> a sort filter id.
     */
    private void showSortFilterIdContainerUI(final int contentFilterId) {
        final FilterContainer subsetFilterContainer =
                searchQHFactory.getContentFilterSortFilterVariant(contentFilterId);

        final FilterContainer supersetFilterContainer =
                contentFilterFidToSupersetSortFilterMap.get(contentFilterId);
        if (subsetFilterContainer != null) {
            if (supersetFilterContainer == null) {
                throw new RuntimeException(
                        "supersetFilterContainer should never be null here");
            }

            setUiItemsVisibility(supersetFilterContainer, false, sortFilterIdToUiItemMap);
            setUiItemsVisibility(subsetFilterContainer, true, sortFilterIdToUiItemMap);
        } else {
            if (supersetFilterContainer != null) {
                setUiItemsVisibility(supersetFilterContainer, false,
                        sortFilterIdToUiItemMap);
            }
        }
        notifySortFiltersVisibility();
    }

    /**
     * This method is only used to show the all sort filters for measurement of the width.
     * <p>
     * See {@link SearchFilterOptionMenuAlikeDialogGenerator}
     */
    protected void showAllAvailableSortFilters() {
        for (int index = 0; index < contentFilterFidToSupersetSortFilterMap.size(); index++) {
            final FilterContainer container =
                    contentFilterFidToSupersetSortFilterMap.valueAt(index);
            if (container != null) {
                setUiItemsVisibility(container, true, sortFilterIdToUiItemMap);
            }
        }
    }

    private void setUiItemsVisibility(
            @Nullable final FilterContainer filters,
            final boolean isVisible,
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap) {
        if (filters != null && filters.getFilterGroups() != null) {
            for (final FilterGroup filterGroup : filters.getFilterGroups()) {
                setUiItemVisible(isVisible, filterIdToUiItemMap, filterGroup.getIdentifier());
                for (final FilterItem item : filterGroup.getFilterItems()) {
                    setUiItemVisible(isVisible, filterIdToUiItemMap, item.getIdentifier());
                }
            }
        }
    }

    private void setUiItemVisible(
            final boolean isVisible,
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap,
            final int id) {
        final IUiItemWrapper uiWrapper = filterIdToUiItemMap.get(id);
        if (uiWrapper != null) {
            uiWrapper.setVisible(isVisible);
        }
    }

    /**
     * Get all sort filter groups for the content filters.
     * It has to have all content filter groups that are available for a service.
     *
     * @param filters the content filters
     * @return the sort filter groups. Empty list if either param filters or no
     * filter groups available
     */
    @NonNull
    private List<FilterGroup> getAllSortFilterGroups(@Nullable final FilterContainer filters) {
        if (filters != null && filters.getFilterGroups() != null) {
            final List<FilterGroup> sortGroups = new ArrayList<>();
            for (final FilterGroup filterGroup : filters.getFilterGroups()) {
                final FilterContainer sf = filterGroup.getAllSortFilters();
                if (sf != null && sf.getFilterGroups() != null) {
                    sortGroups.addAll(sf.getFilterGroups());
                }
            }
            return sortGroups;
        }
        return Collections.emptyList();
    }

    protected void handleIdInNonExclusiveGroup(final int filterId,
                                               @Nullable final IUiItemWrapper uiItemWrapper,
                                               @NonNull final List<Integer> selectedFilter) {
        if (uiItemWrapper != null) { // could be null if there is no UI
            if (uiItemWrapper.isChecked()) {
                if (!selectedFilter.contains(filterId)) {
                    selectedFilter.add(filterId);
                }
            } else { // remove from list
                if (selectedFilter.contains(filterId)) {
                    selectedFilter.remove((Integer) filterId);
                }
            }
        } else { // we have no UI
            if (!selectedFilter.contains(filterId)) {
                selectedFilter.add(filterId);
            } else {
                selectedFilter.remove((Integer) filterId);
            }
        }
    }

    public synchronized void selectContentFilter(final int filterId) {
        selectFilter(filterId, contentFilterIdToUiItemMap, selectedContentFilters,
                contentFilterExclusive);
        showSortFilterIdContainerUI(filterId);
    }

    public synchronized void selectSortFilter(final int filterId) {
        selectFilter(filterId, sortFilterIdToUiItemMap, selectedSortFilters, sortFilterExclusive);
    }

    private void selectFilter(
            final int id,
            @NonNull final SparseArrayCompat<IUiItemWrapper> filterIdToUiItemMap,
            @NonNull final List<Integer> selectedFilter,
            @NonNull final ExclusiveGroups exclusive) {
        final IUiItemWrapper uiItemWrapper =
                filterIdToUiItemMap.get(id);

        // here we remove/add the by the UI (de)selected id.
        if (exclusive.handleIdInExclusiveGroup(id, selectedFilter)) {
            if (uiItemWrapper != null && !uiItemWrapper.isChecked()) {
                uiItemWrapper.setChecked(true);
            }
        } else {
            handleIdInNonExclusiveGroup(id, uiItemWrapper, selectedFilter);
        }
    }

    /**
     * Prepare the content and sort filters {@link FilterItem}'s lists for a now filtered
     * search.
     * <p>
     * If a callback is registered it wil be called with copy's of the local sort and
     * content lists. To avoid concurrently modification of the lists. As they are progressed
     * through async javarx calls. Note: The members aka {@link FilterItem}'s are not copied.
     */
    public void prepareForSearch() {
        createContentFilterItemListFromIdentifierList();
        createSortFilterItemListFromIdentifiersList();

        if (callback != null) {
            callback.selectedFilters(new ArrayList<>(userSelectedContentFilters),
                    new ArrayList<>(userSelectedSortFilters));
        }
    }

    /**
     * This method is meant to be called to add {@link android.view.View}s that represents
     * a content filter.
     * <p>
     * It has to be called within a subclass of {@link SearchFilterLogic} which implements
     * {@link ICreateUiForFiltersWorker} itself or as an any inner class.
     *
     * @param id            the id of a content filter
     * @param uiItemWrapper the wrapped UI {@link android.view.View} for that content filter
     */
    public void addContentFilterUiWrapperToItemMap(
            final int id,
            @NonNull final IUiItemWrapper uiItemWrapper) {
        contentFilterIdToUiItemMap.put(id, uiItemWrapper);
    }

    /**
     * This method is meant to be called to add {@link android.view.View}s that represents
     * a sort filter.
     * <p>
     * It has to be called within a subclass of {@link SearchFilterLogic} which implements
     * {@link ICreateUiForFiltersWorker} itself or as an any inner class.
     *
     * @param id            the id of a sort filter
     * @param uiItemWrapper the wrapped UI {@link android.view.View} for that sort filter
     */
    public void addSortFilterUiWrapperToItemMap(
            final int id,
            @NonNull final IUiItemWrapper uiItemWrapper) {
        sortFilterIdToUiItemMap.put(id, uiItemWrapper);
    }

    /**
     * Wrap a {@link FilterItem} or {@link FilterGroup} to their
     * actual UI element(s) ({@link android.view.View}).
     */
    public interface IUiItemWrapper {
        /**
         * set a view element visible.
         *
         * @param visible true if visible, false if not visible
         */
        void setVisible(boolean visible);

        /**
         * @return get the id of the corresponding {@link FilterItem}
         */
        int getItemId();

        /**
         * Is the UI element selected.
         *
         * @return true if selected
         */
        boolean isChecked();

        /**
         * select the UI element.
         *
         * @param checked select UI element
         */
        void setChecked(boolean checked);
    }

    /**
     * Creating user elements for all filters inside a {@link FilterContainer}.
     *
     * <b>Note:</b> use {@link #addContentFilterUiWrapperToItemMap(int, IUiItemWrapper)} and
     * {@link #addSortFilterUiWrapperToItemMap(int, IUiItemWrapper)} to actually make
     * {@link SearchFilterLogic} aware of them.
     */
    public interface ICreateUiForFiltersWorker {
        /**
         * Will be called before any {@link FilterContainer} looping.
         */
        void prepare();

        /**
         * Create Ui elements specifically related to the {@link FilterGroup} itself.
         * But it could also be used for creating items.
         * <p>
         * -> This method is called *before* the {@link #createFilterItem(FilterItem, FilterGroup)}
         *
         * @param filterGroup one group each time from {@link FilterContainer#getFilterGroups()}
         */
        void createFilterGroupBeforeItems(@NonNull FilterGroup filterGroup);

        /**
         * Create Ui elements specifically related to a {@link FilterItem} itself.
         *
         * @param filterItem  the actual item you should create a UI element here
         * @param filterGroup (optional) one group each time from
         *                    {@link FilterContainer#getFilterGroups()}
         */
        void createFilterItem(@NonNull FilterItem filterItem, @NonNull FilterGroup filterGroup);

        /**
         * Create Ui elements specifically related to the {@link FilterGroup} itself.
         * But it could also be used for creating items.
         * <p>
         * -> This method is called *after* the {@link #createFilterItem(FilterItem, FilterGroup)}
         *
         * @param filterGroup one group each time from {@link FilterContainer#getFilterGroups()}
         */
        void createFilterGroupAfterItems(@NonNull FilterGroup filterGroup);

        /**
         * do anything you might want to clean up or whatever.
         */
        void finish();

        /**
         * Notify if filters are visible. Eg to show or hide 'sort filter' section title
         *
         * @param areFiltersVisible true if filter visible
         */
        void filtersVisible(boolean areFiltersVisible);
    }

    /**
     * This callback will be called if a search with additional filters should occur.
     */
    public interface Callback {
        void selectedFilters(@NonNull List<FilterItem> userSelectedContentFilter,
                             @NonNull List<FilterItem> userSelectedSortFilter);
    }

    /**
     * Track and handle filters of groups in which only one {@link FilterItem} can be selected.
     * <p>
     * We need to track this ourselves as we otherwise rely on androids functionality or lack of
     * tracking the before selected item that now is unselected.
     */
    private static class ExclusiveGroups {

        final SparseArrayCompat<Integer> actualSelectedFilterIdInExclusiveGroupMap =
                new SparseArrayCompat<>();
        /**
         * To quickly determine if a content filter group supports
         * only one item selected (exclusiveness), we need a set that resembles that.
         */
        private final Set<Integer> exclusiveGroupsIdSet = new HashSet<>();
        /**
         * To quickly determine if a content filter id belongs to an exclusive group.
         * This maps works in conjunction with {@link #exclusiveGroupsIdSet}
         */
        private final SparseArrayCompat<Integer> filterIdToGroupIdMap =
                new SparseArrayCompat<>();

        /**
         * Clear {@link #exclusiveGroupsIdSet} and {@link #filterIdToGroupIdMap}.
         */
        public void clear() {
            exclusiveGroupsIdSet.clear();
            filterIdToGroupIdMap.clear();
            actualSelectedFilterIdInExclusiveGroupMap.clear();
        }

        /**
         * Check if filter id is valid.
         *
         * @param filterId the filter id to check
         * @return true if valid
         */
        public boolean filterIdToGroupIdMapContainsId(final int filterId) {
            return filterIdToGroupIdMap.indexOfKey(filterId) >= 0;
        }

        public boolean isFilterIdPartOfAnExclusiveGroup(final int filterId) {
            if (filterIdToGroupIdMapContainsId(filterId)) {
                final int filterGroupId =
                        Objects.requireNonNull(filterIdToGroupIdMap.get(filterId));
                return exclusiveGroupsIdSet.contains(filterGroupId);
            }
            return false;
        }

        /**
         * @param filterId       the id of a {@link FilterItem}
         * @param selectedFilter the list of filter Ids that could contain the given id
         * @return true if exclusive group
         */
        private boolean handleIdInExclusiveGroup(final int filterId,
                                                 @NonNull final List<Integer> selectedFilter) {
            // case exclusive group selection
            if (isFilterIdPartOfAnExclusiveGroup(filterId)) {
                final int previousSelectedId =
                        ifInExclusiveGroupRemovePreviouslySelectedId(filterId);
                if (selectedFilter.contains(previousSelectedId)) {
                    selectedFilter.remove((Integer) previousSelectedId);
                    selectedFilter.add(filterId);
                } else if (previousSelectedId == ITEM_IDENTIFIER_UNKNOWN) {
                    selectedFilter.add(filterId);
                }
                addIdIfBelongsToExclusiveGroup(filterId);
                return true;
            }
            return false;
        }

        /**
         * Insert filter ids with corresponding group ids.
         * <p>
         * We need to know which filter belongs to which group, that we can
         * determine if a selected {@link FilterItem} is part of an exclusive
         * group or not.
         *
         * @param filterId      filter identifier
         * @param filterGroupId group identifier
         */
        public void putFilterIdToItsGroupId(final int filterId, final int filterGroupId) {
            filterIdToGroupIdMap.put(filterId, filterGroupId);
        }

        /**
         * Add exclusive groups to the map.
         *
         * @param groupId the id of the exclusive group
         */
        public void addGroupToExclusiveGroupsMap(final int groupId) {
            exclusiveGroupsIdSet.add(groupId);
        }

        private void addIdIfBelongsToExclusiveGroup(final int filterId) {
            final int filterGroupId =
                    Objects.requireNonNull(filterIdToGroupIdMap.get(filterId));
            if (exclusiveGroupsIdSet.contains(filterGroupId)) {
                actualSelectedFilterIdInExclusiveGroupMap.put(filterGroupId, filterId);
            }
        }

        /**
         * check if the filter group id for a given filter id is already in a exclusive group.
         * <p>
         * If so remove the group filter id.
         *
         * @param filterId the id of a filter that might belong to an exclusive filter group
         * @return id of removed filter id from {@link #actualSelectedFilterIdInExclusiveGroupMap}
         * otherwise {@link FilterContainer#ITEM_IDENTIFIER_UNKNOWN}
         */

        private int ifInExclusiveGroupRemovePreviouslySelectedId(final int filterId) {
            int previousFilterId = ITEM_IDENTIFIER_UNKNOWN;
            final int filterGroupId =
                    Objects.requireNonNull(filterIdToGroupIdMap.get(filterId));

            final int index = actualSelectedFilterIdInExclusiveGroupMap.indexOfKey(filterGroupId);
            if (exclusiveGroupsIdSet.contains(filterGroupId) && index >= 0) {
                previousFilterId = actualSelectedFilterIdInExclusiveGroupMap.valueAt(index);
                actualSelectedFilterIdInExclusiveGroupMap.removeAt(index);
            }
            return previousFilterId;
        }
    }

    public static final class Factory {
        private Factory() {
        }

        /**
         * Create variant of {@link SearchFilterLogic}.
         *
         * @param logicVariant    the variant {@link Variant}.
         * @param searchQHFactory of the service
         * @param callback        if you want to get the data the user has requested by calling
         *                        {@link SearchFilterLogic#prepareForSearch()}
         * @return instance of {@link SearchFilterLogic}.
         */
        @NonNull
        public static SearchFilterLogic create(
                @NonNull final Variant logicVariant,
                @NonNull final SearchQueryHandlerFactory searchQHFactory,
                @Nullable final Callback callback) {
            switch (logicVariant) {

                case SEARCH_FILTER_LOGIC_LEGACY: // the case we are using SearchFragmentLegacy
                    return new SearchFilterLogic(searchQHFactory, callback) {
                        @Override
                        protected void handleIdInNonExclusiveGroup(
                                final int filterId,
                                @Nullable final IUiItemWrapper uiItemWrapper,
                                @NonNull final List<Integer> selectedFilter) {

                            if (null != uiItemWrapper) {
                                // for the action menu based UI we have to toggle first
                                // to be compatible with the SearchFilterLogic
                                uiItemWrapper.setChecked(!uiItemWrapper.isChecked());
                            }
                            super.handleIdInNonExclusiveGroup(
                                    filterId, uiItemWrapper, selectedFilter);
                        }
                    };

                default:
                case SEARCH_FILTER_LOGIC_DEFAULT:
                    return new SearchFilterLogic(searchQHFactory, callback);
            }
        }

        public enum Variant {
            SEARCH_FILTER_LOGIC_DEFAULT,
            SEARCH_FILTER_LOGIC_LEGACY
        }
    }
}
