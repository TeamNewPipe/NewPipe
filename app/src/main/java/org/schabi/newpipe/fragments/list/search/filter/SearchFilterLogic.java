package org.schabi.newpipe.fragments.list.search.filter;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.collection.LongSparseArray;

/**
 * Main public interaction methods.
 */
interface CallbackOptions {
    void prepareForSearch();

    ArrayList<Integer> getSelectedContentFilters();

    ArrayList<Integer> getSelectedSortFilters();
}

public abstract class SearchFilterLogic implements CallbackOptions {

    // Menu groups identifier
    protected static final int MENU_GROUP_SEARCH_BUTTON = 0;
    protected static final int MENU_GROUP_CONTENT_FILTER = 1;
    protected static final int MENU_GROUP_LAST_CONTENT_FILTER = 2;
    protected static final int ITEM_IDENTIFIER_UNKNOWN = -1;
    /**
     * sort filter groups that are only allowed to have one exclusive item selected.
     */
    final Map<Integer, Integer> exclusiveGroupsThatOnlyAllowOneItemSelectedMap = new HashMap<>();
    /**
     * mapping of above groups {@link #exclusiveGroupsThatOnlyAllowOneItemSelectedMap}
     * to actual selected item. This is later used to make sure only one
     * item can be selected in a Menu group.
     * <p>
     * This is as workaround as there is no sort of 'onEventChangeListener'
     * for MenuItems available.
     */
    final Map<Integer, Integer> exclusiveGroupsToItemSelectedMap = new HashMap<>();
    private final Callback callback;
    private final Map<Integer, UiItemWrapper> contentFilterIdToItemMap = new HashMap<>();
    private final LongSparseArray<UiItemWrapper> allSortFilterIdToItemMap =
            new LongSparseArray<>();
    private final LongSparseArray<UiItemWrapper> chosenVariantSortFilterIdToItemMap =
            new LongSparseArray<>();
    ArrayList<Integer> userSelectedSortFilterList = null;
    /**
     * the current selected content filter id. It defaults to the first found
     * content filter entry in the given FilterGroup.
     */
    int selectedContentFilterId;
    // added by fb
    private StreamingService service = null;

    public SearchFilterLogic(final Callback callback) {
        this.callback = callback;
    }

    // copy paste from original SearchFragment
    private void restoreContentFilterChecked(final int itemId) {
        if (itemId != ITEM_IDENTIFIER_UNKNOWN) {
            final UiItemWrapper item = contentFilterIdToItemMap.get(itemId);
            if (item == null) {
                return;
            }

            item.setChecked(true);
        }
    }

    public void updateService(final StreamingService streamingService) {
        this.service = streamingService;
    }

    protected void createFilterLogic() {
        createContentFilterMenuEntries(false);
        restoreContentFilterChecked(selectedContentFilterId); // is used only for content filter

        createSortFilterMenuEntries(MENU_GROUP_LAST_CONTENT_FILTER, false);
        restoreSortFilterChecked(selectedContentFilterId);
    }

    /**
     * Restore the state of the sort filters.
     * <p>
     * call only after {@link #createSortFilterMenuEntries(int, boolean)}
     *
     * @param contentFilterId
     */
    private void restoreSortFilterChecked(final int contentFilterId) {
        selectMenuItemsForSortFilterVariant(contentFilterId, false);
        setMenuItemsInvisibleForSelectedSortFilterVariant();
        recheckPreviousSelectedItems();
    }

    private void recheckPreviousSelectedItems() {
        for (final int key : userSelectedSortFilterList) {
            final UiItemWrapper item = chosenVariantSortFilterIdToItemMap.get(key);
            if (null != item) {
                item.setChecked(true);
                ifExclusiveGroupAddSelectedItem(item);
            }
        }
    }

    private void setMenuItemsInvisibleForSelectedSortFilterVariant() {
        for (int i = 0; i < allSortFilterIdToItemMap.size(); i++) {
            final long key = allSortFilterIdToItemMap.keyAt(i);
            if (!chosenVariantSortFilterIdToItemMap.containsKey(key)) {
                final UiItemWrapper item = allSortFilterIdToItemMap.get(key);
                if (item != null) {
                    item.setVisible(false);
                }
            }
        }
    }

    private void selectMenuItemsForSortFilterVariant(final int contentFilterId,
                                                     final boolean isInit) {
        // always first clear this map. As sortFilters or sortGroups can be null (in case
        // there are no sort{Filters,Groups} defined for a given contentFilterId
        chosenVariantSortFilterIdToItemMap.clear();

        final Filter sortFilters = service.getSearchQHFactory()
                .getContentFilterSortFilterVariant(contentFilterId);
        if (sortFilters == null) {
            return;
        }
        final FilterGroup[] sortGroups = sortFilters.getFilterGroups();
        if (sortGroups == null) {
            return;
        }

        // 'true' means nothing previously stored
        final boolean hasNoPreviouslyStoredSelectedSortFilter =
                userSelectedSortFilterList.isEmpty();

        for (final FilterGroup sortGroup : sortGroups) {

            if (!isInit) {
                final UiItemWrapper sortGroupItem =
                        allSortFilterIdToItemMap.get(sortGroup.identifier);
                chosenVariantSortFilterIdToItemMap.put(sortGroup.identifier, sortGroupItem);
                if (sortGroup.filterItems.length > 0) {
                    setMenuItemVisible(sortGroupItem);
                }
            }

            boolean isFirstItem = sortGroup.onlyOneCheckable;

            // check first item if in a group only one item could be selected at a time
            for (final FilterItem filter : sortGroup.filterItems) {

                UiItemWrapper sortMenuItem = null;
                if (!isInit) {
                    sortMenuItem = allSortFilterIdToItemMap.get(filter.getIdentifier());

                    if (sortMenuItem == null) {
                        continue;
                    }
                    chosenVariantSortFilterIdToItemMap.put(filter.getIdentifier(), sortMenuItem);
                    setMenuItemVisible(sortMenuItem);
                }

                if (isFirstItem) { // select first item if possible
                    if(userSelectedSortFilterList.size() == 0){
                        userSelectedSortFilterList.add(filter.getIdentifier());
                    } else if(!chosenVariantSortFilterIdToItemMap.containsKey(userSelectedSortFilterList.get(0))){
                        userSelectedSortFilterList.clear();
                        userSelectedSortFilterList.add(filter.getIdentifier());
                    }
                    if (!isInit) {
                        sortMenuItem.setChecked(true);
                        ifExclusiveGroupAddSelectedItem(sortMenuItem);
                    }
                    isFirstItem = false;
                }
            }
        }
    }

    private void ifExclusiveGroupAddSelectedItem(final UiItemWrapper item) {
        if (exclusiveGroupsThatOnlyAllowOneItemSelectedMap.containsKey(item.getGroupId())) {
            exclusiveGroupsToItemSelectedMap.put(item.getGroupId(), item.getItemId());
        }
    }

    private int ifExclusiveGroupRemoveSelectedItem(final UiItemWrapper item) {
        int itemId = ITEM_IDENTIFIER_UNKNOWN;
        if (exclusiveGroupsThatOnlyAllowOneItemSelectedMap.containsKey(item.getGroupId())) {
            if (exclusiveGroupsToItemSelectedMap.containsKey(item.getGroupId())) {
                itemId = exclusiveGroupsToItemSelectedMap.get(item.getGroupId());
                exclusiveGroupsToItemSelectedMap.remove(item.getGroupId());
            }
        }
        return itemId;
    }

    private void setMenuItemVisible(final UiItemWrapper item) {
        if (item != null) {
            item.setVisible(true);
        }
    }

    private void createContentFilterMenuEntries(final boolean isInit) {
        contentFilterIdToItemMap.clear();

        final FilterGroup[] contentFilterGroups =
                service.getSearchQHFactory().getAvailableContentFilter().getFilterGroups();

        // if > 0 we have content filters -> create the 'search'-button
        //if (contentFilterGroups.length > 0) {
        if (!isInit) {
            createContentFilterPre();
        }
        //}
        boolean isFirstItem = true;
        for (final FilterGroup contentFilterGroup : contentFilterGroups) {
            if (contentFilterGroup.filterItems.length > 0 && contentFilterGroup.groupName != null) {
                if (!isInit) {
                    createContentFilterGroup(contentFilterGroup);
                }
            }

            // check first item if in a group only one item could be selected at a time
            for (final FilterItem filter : contentFilterGroup.filterItems) {

                final int filterIdentifier = filter.getIdentifier();
                if (!isInit) {
                    createContentFilterGroupElement(filter);
                }

                // set the default content filter to the first content filter item found
                if (isFirstItem && selectedContentFilterId == ITEM_IDENTIFIER_UNKNOWN) {
                    selectedContentFilterId = filterIdentifier;
                    isFirstItem = false;
                }
            }
            if (!isInit) {
                createContentFilterGroupFinished(contentFilterGroup);
            }
        }
    }

    private void createSortFilterMenuEntries(int lastUsedGroupId,
                                             final boolean isInit) {
        final Filter sortFilters = service.getSearchQHFactory().getAvailableSortFilter();
        if (sortFilters == null) {
            return;
        }

        final FilterGroup[] sortGroups = sortFilters.getFilterGroups();

        if (sortGroups == null) {
            return;
        }

        allSortFilterIdToItemMap.clear();
        exclusiveGroupsThatOnlyAllowOneItemSelectedMap.clear();

        for (final FilterGroup sortGroup : sortGroups) {
            if (sortGroup.filterItems.length > 0 && sortGroup.groupName != null) {
                ++lastUsedGroupId;
                if (!isInit) {
                    createSortFilterGroup(sortGroup, lastUsedGroupId);
                }
            } else if (sortGroup.groupName == null) {
                // skip group name entry as there is no name but keep it in separate group
                ++lastUsedGroupId;
            }

            // check first item if in a group only one item could be selected at a time
            for (final FilterItem filter : sortGroup.filterItems) {

                if (!isInit) {
                    createSortFilterGroupElement(filter, lastUsedGroupId);
                }
            }

            if (!isInit) {
                createSortFilterGroupFinished(sortGroup, lastUsedGroupId);
            }

            if (sortGroup.onlyOneCheckable) {
                exclusiveGroupsThatOnlyAllowOneItemSelectedMap
                        .put(lastUsedGroupId, lastUsedGroupId);
            }
        }
    }

    public void prepareForSearch() {
        final List<FilterItem> selectedSortFilterToSendToLib = new ArrayList<>();
        for (final int itemId : userSelectedSortFilterList) {
            final boolean hasItemId = chosenVariantSortFilterIdToItemMap.containsKey(itemId);
            if (hasItemId) {
                selectedSortFilterToSendToLib.add(
                        service.getSearchQHFactory().getFilterItem(itemId));
            }
        }
        callbackPuser(selectedSortFilterToSendToLib, false);
    }

    public ArrayList<Integer> getSelectedContentFilters() {
        return new ArrayList<Integer>() {
            {
                add(selectedContentFilterId);
            }
        };
    }

    public ArrayList<Integer> getSelectedSortFilters() {
        return userSelectedSortFilterList;
    }

    /**
     * The implementing UI class should call this method if a content filter is selected.
     * {@link SearchFilterUI}
     *
     * @param filterId
     */
    protected void selectContentFilter(final int filterId) {
        if (selectedContentFilterId != filterId) {
            selectedContentFilterId = filterId;
            restoreSortFilterChecked(selectedContentFilterId);
            final UiItemWrapper menuItem =
                    contentFilterIdToItemMap.get(selectedContentFilterId);
            if (menuItem != null) {
                menuItem.setChecked(!menuItem.isChecked());
            }
        }
    }

    /**
     * The implementing UI class should call this method if a sort filter is selected.
     * {@link SearchFilterUI}
     *
     * @param filterId
     */
    protected void selectSortFilter(final int filterId) {
        final UiItemWrapper item = chosenVariantSortFilterIdToItemMap.get(filterId);
        item.setChecked(!item.isChecked());
        exclusiveStuff(item, userSelectedSortFilterList);
    }

    private void exclusiveStuff(final UiItemWrapper item,
                                final List<Integer> userSelectedFilterList) {
        // case exclusive group selection
        if (exclusiveGroupsThatOnlyAllowOneItemSelectedMap
                .containsKey(item.getGroupId())) {
            final int itemId = ifExclusiveGroupRemoveSelectedItem(item);
            if (userSelectedFilterList.contains(itemId)) {
                userSelectedFilterList.remove((Integer) itemId);
            }
            userSelectedFilterList.add(item.getItemId());
            ifExclusiveGroupAddSelectedItem(item);
        } else {

            // case not exclusive group selection
            if (item.isChecked()) {
                if (!userSelectedFilterList.contains(item.getItemId())) {
                    userSelectedFilterList.add(item.getItemId());
                }
            } else { // remove from list
                if (userSelectedFilterList.contains(item.getItemId())) {
                    userSelectedFilterList.remove((Integer) item.getItemId());
                }
            }
        }
    }

    public void restorePreviouslySelectedFilters(final ArrayList<Integer> contentFilter,
                                                 final ArrayList<Integer> sortFilters) {
        // we currently work with only one contentFilter selectable at a time
        if (contentFilter != null && !contentFilter.isEmpty()) {
            selectedContentFilterId = contentFilter.get(0);
        } else {
            selectedContentFilterId = ITEM_IDENTIFIER_UNKNOWN;
        }

        if (sortFilters != null) {
            userSelectedSortFilterList = sortFilters;
        } else {
            userSelectedSortFilterList = new ArrayList<>();
        }

        createContentFilterMenuEntries(true);
        createSortFilterMenuEntries(MENU_GROUP_LAST_CONTENT_FILTER, true);
        selectMenuItemsForSortFilterVariant(selectedContentFilterId, true);

        final List<FilterItem> newList = new ArrayList<>();
        for (final int id : userSelectedSortFilterList) {
            newList.add(service.getSearchQHFactory().getFilterItem(id));
        }
        callbackPuser(newList, true);
    }

    private void callbackPuser(final List<FilterItem> selectedSortFilterToSendToLib,
                               final boolean isInit) {
        if (callback != null) {
            final List<FilterItem> selectedContentFilters = new ArrayList<>();

            selectedContentFilters.add(
                    service.getSearchQHFactory().getFilterItem(selectedContentFilterId));
            callback.selectedFilters(selectedContentFilters, selectedSortFilterToSendToLib, isInit);
        }
    }

    protected void addContentFilterIdToItemMap(final int id, final UiItemWrapper item) {
        contentFilterIdToItemMap.put(id, item);
    }

    protected void addToAllSortFilterItToItemMap(final int id, final UiItemWrapper item) {
        allSortFilterIdToItemMap.put(id, item);
    }

    protected abstract void createContentFilterPre();

    protected abstract void createContentFilterGroup(FilterGroup contentGroup);

    protected abstract void createContentFilterGroupElement(FilterItem contentFilterGroupElement);

    protected abstract void createContentFilterGroupFinished(FilterGroup contentGroup);

    protected abstract void createSortFilterGroup(FilterGroup sortGroup, int lastUsedGroupId);

    protected abstract void createSortFilterGroupElement(FilterItem filter, int lastUsedGroupId);

    protected abstract void createSortFilterGroupFinished(FilterGroup sortGroup,
                                                          int lastUsedGroupId);

    /**
     * Wraps around a single UI Element that represents a Filter.
     */
    interface UiItemWrapper {
        void setVisible(boolean visible);

        int getItemId();

        int getGroupId();

        boolean isChecked();

        void setChecked(boolean checked);
    }

    /**
     * This callback will be called if a search with additional filters should occur.
     */
    public interface Callback {
        void selectedFilters(List<FilterItem> theSelectedContentFilter,
                             List<FilterItem> theSelectedSortFilter, boolean isInit);
    }
}
