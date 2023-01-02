package org.schabi.newpipe.fragments.list.search

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.schabi.newpipe.App
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.ExtractorHelper
import java.util.concurrent.TimeUnit

class SearchViewModel(
    application: Application,
    private val serviceId: Int,
    private val showLocalSuggestions: Boolean,
    private val showRemoteSuggestions: Boolean
) : ViewModel() {
    private val historyRecordManager = HistoryRecordManager(application)
    private val suggestionPublisher = PublishSubject.create<String>()

    private val suggestionMutableLiveData = MutableLiveData<Notification<List<SuggestionItem>>>()
    val suggestionLiveData: LiveData<Notification<List<SuggestionItem>>>
        get() = suggestionMutableLiveData

    private val suggestionDisposable = suggestionPublisher
        .startWithItem("")
        .distinctUntilChanged()
        .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
        .switchMap { query: String ->
            // Only show remote suggestions if they are enabled in settings and
            // the query length is at least THRESHOLD_NETWORK_SUGGESTION
            val shallShowRemoteSuggestionsNow = showRemoteSuggestions &&
                query.length >= THRESHOLD_NETWORK_SUGGESTION
            if (showLocalSuggestions && shallShowRemoteSuggestionsNow) {
                Observable.zip(
                    getLocalSuggestionsObservable(query, 3),
                    getRemoteSuggestionsObservable(query)
                ) { local, remote -> (local + remote).distinct() }.materialize()
            } else if (showLocalSuggestions) {
                getLocalSuggestionsObservable(query, 25).materialize()
            } else if (shallShowRemoteSuggestionsNow) {
                getRemoteSuggestionsObservable(query).materialize()
            } else {
                Observable.just(emptyList<SuggestionItem>()).materialize()
            }
        }
        .subscribe({
            suggestionMutableLiveData.postValue(it)
        }) {
            suggestionMutableLiveData.postValue(Notification.createOnError(it))
        }

    override fun onCleared() {
        suggestionDisposable.dispose()
    }

    fun updateSearchQuery(query: String) {
        suggestionPublisher.onNext(query)
    }

    private fun getLocalSuggestionsObservable(query: String, similarQueryLimit: Int): Observable<List<SuggestionItem>> {
        return historyRecordManager.getRelatedSearches(query, similarQueryLimit, 25)
            .toObservable()
            .map { entries -> entries.map { SuggestionItem(true, it) } }
    }

    private fun getRemoteSuggestionsObservable(query: String): Observable<List<SuggestionItem>> {
        return ExtractorHelper.getSuggestionsFor(serviceId, query)
            .toObservable()
            .map { entries -> entries.map { SuggestionItem(false, it) } }
    }

    companion object {
        /**
         * How much time have to pass without emitting a item (i.e. the user stop typing)
         * to fetch/show the suggestions, in milliseconds.
         */
        private const val SUGGESTIONS_DEBOUNCE = 120L // ms

        /**
         * The suggestions will only be fetched from network if the query meet this threshold (>=).
         * (local ones will be fetched regardless of the length)
         */
        private const val THRESHOLD_NETWORK_SUGGESTION = 1

        fun getFactory(
            serviceId: Int,
            showLocalSuggestions: Boolean,
            showRemoteSuggestions: Boolean
        ) = viewModelFactory {
            initializer {
                SearchViewModel(App.getApp(), serviceId, showLocalSuggestions, showRemoteSuggestions)
            }
        }
    }
}
