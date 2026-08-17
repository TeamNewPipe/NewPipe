package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.ExtractorHelper

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRecordManager = HistoryRecordManager(application)

    private val _searchResults = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val searchResults: StateFlow<List<StreamInfoItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    val searchHistory: StateFlow<List<String>> = _queryInput
        .flatMapLatest { query ->
            historyRecordManager.getRelatedSearches(query.trim(), 10, 30)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _queryInput.value = newQuery
    }

    fun search(serviceId: Int, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            
            // Asynchronous non-blocking search history save
            launch(Dispatchers.IO) {
                try {
                    historyRecordManager.onSearched(serviceId, trimmed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                val info = ExtractorHelper.searchFor(serviceId, trimmed, emptyList<String>(), null)
                val streamItems = withContext(Dispatchers.Default) {
                    info.relatedItems.filterIsInstance<StreamInfoItem>()
                }
                _searchResults.value = streamItems
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRecordManager.deleteSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRecordManager.deleteCompleteSearchHistory()
        }
    }
}

