package org.schabi.newpipe.fragments.list.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.fragments.list.search.filter.InjectFilterItem
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic
import org.schabi.newpipe.fragments.list.search.filter.SearchFilterLogic.Factory.Variant

/**
 * This class hosts the search filters logic. It facilitates
 * the communication with the SearchFragment* and the *DialogFragment
 * based search filter UI's
 */
class SearchViewModel(
    val serviceId: Int,
    logicVariant: Variant,
    userSelectedContentFilterList: List<Int>,
    userSelectedSortFilterList: List<Int>
) : ViewModel() {

    private val selectedContentFilterMutableLiveData: MutableLiveData<MutableList<FilterItem>> =
        MutableLiveData()
    private var selectedSortFilterLiveData: MutableLiveData<MutableList<FilterItem>> =
        MutableLiveData()
    private var userSelectedSortFilterListMutableLiveData: MutableLiveData<ArrayList<Int>> =
        MutableLiveData()
    private var userSelectedContentFilterListMutableLiveData: MutableLiveData<ArrayList<Int>> =
        MutableLiveData()
    private var doSearchMutableLiveData: MutableLiveData<Boolean> = MutableLiveData()

    val selectedContentFilterItemListLiveData: LiveData<MutableList<FilterItem>>
        get() = selectedContentFilterMutableLiveData
    val selectedSortFilterItemListLiveData: LiveData<MutableList<FilterItem>>
        get() = selectedSortFilterLiveData
    val userSelectedContentFilterListLiveData: LiveData<ArrayList<Int>>
        get() = userSelectedContentFilterListMutableLiveData
    val userSelectedSortFilterListLiveData: LiveData<ArrayList<Int>>
        get() = userSelectedSortFilterListMutableLiveData
    val doSearchLiveData: LiveData<Boolean>
        get() = doSearchMutableLiveData

    var searchFilterLogic: SearchFilterLogic

    init {
        // inject before creating SearchFilterLogic
        InjectFilterItem.DividerBetweenYoutubeAndYoutubeMusic.run()

        searchFilterLogic = SearchFilterLogic.Factory.create(
            logicVariant,
            NewPipe.getService(serviceId).searchQHFactory, null
        )
        searchFilterLogic.restorePreviouslySelectedFilters(
            userSelectedContentFilterList,
            userSelectedSortFilterList
        )

        searchFilterLogic.setCallback { userSelectedContentFilter: List<FilterItem?>,
            userSelectedSortFilter: List<FilterItem?> ->
            selectedContentFilterMutableLiveData.value =
                userSelectedContentFilter as MutableList<FilterItem>
            selectedSortFilterLiveData.value =
                userSelectedSortFilter as MutableList<FilterItem>
            userSelectedContentFilterListMutableLiveData.value =
                searchFilterLogic.selectedContentFilters
            userSelectedSortFilterListMutableLiveData.value =
                searchFilterLogic.selectedSortFilters

            doSearchMutableLiveData.value = true
        }
    }

    fun weConsumedDoSearchLiveData() {
        doSearchMutableLiveData.value = false
    }

    companion object {

        fun getFactory(
            serviceId: Int,
            logicVariant: Variant,
            userSelectedContentFilterList: ArrayList<Int>,
            userSelectedSortFilterList: ArrayList<Int>
        ) = viewModelFactory {
            initializer {
                SearchViewModel(
                    serviceId,
                    logicVariant,
                    userSelectedContentFilterList,
                    userSelectedSortFilterList
                )
            }
        }
    }
}
