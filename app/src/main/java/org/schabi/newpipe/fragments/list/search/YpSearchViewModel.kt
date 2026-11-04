/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.search

import android.app.Application
import android.content.Intent
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import io.github.yewplay.core.model.YpChannelItem
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.core.model.YpPlaylistItem
import io.github.yewplay.core.model.YpStreamItem
import io.github.yewplay.feature.search.SearchError
import io.github.yewplay.feature.search.SearchNotice
import io.github.yewplay.feature.search.SearchSuggestion
import io.github.yewplay.feature.search.SearchUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ktx.isInterruptedCaused
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.DependentPreferenceHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.ImageStrategy

/** One-off things the screen asks of the fragment. */
sealed interface SearchEvent {
    /** The query was a link that NewPipe can open by itself. */
    data class OpenLink(val intent: Intent) : SearchEvent

    /** A non-fatal error, shown as a snackbar. */
    data class ShowError(val errorInfo: ErrorInfo) : SearchEvent
}

/**
 * YewPlay: state of the Compose search screen. The search itself is NewPipe's: the same
 * extractor calls, search history, suggestions, pagination and local subscriptions as the
 * previous SearchFragment.
 */
class YpSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val historyRecordManager = HistoryRecordManager(application)
    private val subscriptionManager = SubscriptionManager(application)
    private val disposables = CompositeDisposable()
    private val suggestionPublisher = PublishSubject.create<String>()
    private var searchDisposable: Disposable? = null
    private var suggestionDisposable: Disposable? = null

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SearchEvent> = _events

    var serviceId: Int = 0
        private set
    var searchString: String = ""
        private set
    var contentFilter: List<String> = emptyList()
        private set

    /** The error shown in place of the results, for its buttons. */
    var lastError: ErrorInfo? = null
        private set

    private var service: StreamingService? = null
    private var filters: List<String> = emptyList()
    private val sortFilter = ""
    private var lastSearchedString: String = ""
    private var nextPage: Page? = null
    private var rawItems: List<InfoItem> = emptyList()
    private var initialized = false
    private var showLocalSuggestions = true
    private var showRemoteSuggestions = true

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(serviceId: Int, query: String, filter: String?) {
        if (initialized) {
            return
        }
        initialized = true
        this.serviceId = serviceId
        service = runCatching { NewPipe.getService(serviceId) }.getOrNull()
        filters = service?.searchQHFactory?.availableContentFilter?.toList().orEmpty()
        contentFilter = listOfNotNull(filter?.takeIf { it in filters })

        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        showLocalSuggestions = NewPipeSettings.showLocalSearchSuggestions(app, prefs)
        showRemoteSuggestions = NewPipeSettings.showRemoteSearchSuggestions(app, prefs)

        _state.update {
            it.copy(
                query = query,
                hint = hint(),
                filters = filters.map { f -> ServiceHelper.getTranslatedFilterString(f, app) },
                selectedFilter = selectedFilterIndex(),
                showSuggestions = query.isBlank()
            )
        }
        initSuggestionObserver(query)
        if (query.isBlank()) {
            requestFocus()
        } else {
            search(query)
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Field and suggestions                                                                     */
    /* ---------------------------------------------------------------------------------------- */

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text, showSuggestions = suggestionsEnabled()) }
        suggestionPublisher.onNext(text.trim())
    }

    fun onFieldFocused() {
        if (suggestionsEnabled() && !_state.value.showSuggestions) {
            _state.update { it.copy(showSuggestions = true) }
        }
    }

    fun onClear() {
        _state.update {
            it.copy(
                query = "",
                correction = null,
                suggestions = emptyList(),
                showSuggestions = suggestionsEnabled() || it.items.isEmpty()
            )
        }
        suggestionPublisher.onNext("")
        requestFocus()
    }

    /** Puts a text in the field and opens the keyboard, without searching. */
    fun editQuery(text: String) {
        _state.update { it.copy(query = text, showSuggestions = suggestionsEnabled()) }
        suggestionPublisher.onNext(text)
        requestFocus()
    }

    /**
     * Back while the suggestions cover the results: close them and keep the results.
     *
     * @return whether the back press was handled
     */
    fun onBackPressed(): Boolean {
        val current = _state.value
        if (current.showSuggestions && current.items.isNotEmpty() && !current.isLoading) {
            _state.update { it.copy(showSuggestions = false, query = lastSearchedString) }
            return true
        }
        return false
    }

    fun deleteFromHistory(query: String) {
        disposables.add(
            historyRecordManager.deleteSearchHistory(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { suggestionPublisher.onNext(_state.value.query.trim()) },
                    { emitError(ErrorInfo(it, UserAction.DELETE_FROM_HISTORY, "Deleting item failed")) }
                )
        )
    }

    private fun suggestionsEnabled() = showLocalSuggestions || showRemoteSuggestions

    private fun requestFocus() {
        _state.update { it.copy(focusRequest = it.focusRequest + 1) }
    }

    private fun initSuggestionObserver(initialQuery: String) {
        suggestionDisposable?.dispose()
        suggestionDisposable = suggestionPublisher
            .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
            .startWithItem(initialQuery)
            .switchMap { query -> suggestionsFor(query).materialize() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { notification ->
                    val value = notification.value
                    val error = notification.error
                    if (value != null) {
                        _state.update { it.copy(suggestions = value) }
                    } else if (error != null && !error.isInterruptedCaused) {
                        emitError(
                            ErrorInfo(error, UserAction.GET_SUGGESTIONS, searchString, serviceId)
                        )
                    }
                },
                { emitError(ErrorInfo(it, UserAction.GET_SUGGESTIONS, searchString, serviceId)) }
            )
    }

    private fun suggestionsFor(query: String): Observable<List<SearchSuggestion>> {
        val remoteNow = showRemoteSuggestions && query.length >= THRESHOLD_NETWORK_SUGGESTION
        return when {
            showLocalSuggestions && remoteNow -> Observable.zip(
                localSuggestions(query, 3),
                remoteSuggestions(query),
                BiFunction(::mergeSuggestions)
            )

            showLocalSuggestions -> localSuggestions(query, 25)

            remoteNow -> remoteSuggestions(query)

            else -> Observable.just(emptyList())
        }
    }

    private fun mergeSuggestions(
        local: List<SearchSuggestion>,
        remote: List<SearchSuggestion>
    ): List<SearchSuggestion> = local + remote.filter { r -> local.none { it.query == r.query } }

    private fun localSuggestions(query: String, limit: Int): Observable<List<SearchSuggestion>> = historyRecordManager.getRelatedSearches(query, limit, 25)
        .toObservable()
        .map { entries -> entries.map { SearchSuggestion(it, fromHistory = true) } }

    private fun remoteSuggestions(query: String): Observable<List<SearchSuggestion>> = ExtractorHelper.suggestionsFor(serviceId, query)
        .toObservable()
        .map { entries -> entries.map { SearchSuggestion(it, fromHistory = false) } }

    /* ---------------------------------------------------------------------------------------- */
    /* Search                                                                                    */
    /* ---------------------------------------------------------------------------------------- */

    fun onFilterSelect(index: Int) {
        val filter = filters.getOrNull(index) ?: return
        contentFilter = listOf(filter)
        _state.update { it.copy(selectedFilter = index, hint = hint()) }
        if (searchString.isNotEmpty()) {
            search(searchString)
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return
        }
        // a link that NewPipe can open by itself (video, channel, playlist)
        val linkService = runCatching { NewPipe.getServiceByUrl(trimmed) }.getOrNull()
        if (linkService != null) {
            openLink(linkService, trimmed)
            return
        }

        lastSearchedString = searchString
        searchString = trimmed
        nextPage = null
        rawItems = emptyList()
        lastError = null
        _state.update {
            it.copy(
                query = trimmed,
                showSuggestions = false,
                searchId = it.searchId + 1,
                items = emptyList(),
                featureFirstChannel = isAllFilter(),
                notices = emptyList(),
                correction = null,
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                isEmpty = false,
                error = null
            )
        }

        // store the query, when the search history is enabled
        disposables.add(
            historyRecordManager.onSearched(serviceId, trimmed)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    { emitError(ErrorInfo(it, UserAction.SEARCHED, trimmed, serviceId)) }
                )
        )
        suggestionPublisher.onNext(trimmed)

        searchDisposable?.dispose()
        searchDisposable = ExtractorHelper.searchFor(serviceId, trimmed, contentFilter, sortFilter)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleResult, ::handleSearchError)
    }

    fun retry() {
        if (searchString.isNotEmpty()) {
            search(searchString)
        }
    }

    fun loadMore() {
        val page = nextPage
        val current = _state.value
        if (!Page.isValid(page) || current.isLoading || current.isLoadingMore) {
            return
        }
        _state.update { it.copy(isLoadingMore = true) }
        searchDisposable?.dispose()
        searchDisposable = ExtractorHelper
            .getMoreSearchItems(serviceId, searchString, contentFilter, sortFilter, page)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result -> handleNextItems(result, page) },
                { error ->
                    // keep what was loaded; stop asking for more pages
                    _state.update { it.copy(isLoadingMore = false, hasMore = false) }
                    emitError(
                        ErrorInfo(error, UserAction.SEARCHED, searchString, serviceId, browserUrl())
                    )
                }
            )
    }

    /**
     * Finds the extractor item behind an item of the screen (for the long-press menu).
     *
     * @param item an item of the screen
     * @return the stream item, if the item is a stream of the current results
     */
    fun streamItemFor(item: YpListItem): StreamInfoItem? = rawItems
        .firstOrNull { it.url == item.url && it.serviceId == item.serviceId } as? StreamInfoItem

    /**
     * Finds the extractor channel behind an item of the screen.
     *
     * @param item an item of the screen
     * @return the channel item, if the item is a channel of the current results
     */
    fun channelItemFor(item: YpListItem): ChannelInfoItem? = rawItems
        .firstOrNull { it.url == item.url && it.serviceId == item.serviceId } as? ChannelInfoItem

    fun browserUrl(): String? = runCatching {
        service?.searchQHFactory?.getUrl(searchString, contentFilter, sortFilter)
    }.getOrNull()

    private fun openLink(linkService: StreamingService, url: String) {
        _state.update { it.copy(isLoading = true, showSuggestions = false, error = null) }
        disposables.add(
            Single.fromCallable { NavigationHelper.getIntentByLink(app, linkService, url) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { intent ->
                        _state.update { it.copy(isLoading = false) }
                        _events.tryEmit(SearchEvent.OpenLink(intent))
                    },
                    {
                        _state.update { state ->
                            state.copy(
                                isLoading = false,
                                error = SearchError(
                                    message = app.getString(R.string.unsupported_url),
                                    actionLabel = null,
                                    canRetry = false,
                                    canOpenInBrowser = false
                                )
                            )
                        }
                    }
                )
        )
    }

    private fun handleResult(result: SearchInfo) {
        val errors = result.errors
        if (errors.isNotEmpty() &&
            !(errors.size == 1 && errors[0] is SearchExtractor.NothingFoundException)
        ) {
            emitError(ErrorInfo(errors, UserAction.SEARCHED, searchString, serviceId, browserUrl()))
        }
        lastSearchedString = searchString
        nextPage = result.nextPage
        val items = result.relatedItems.filter(::isSupported)
        rawItems = items
        val correction = result.searchSuggestion?.trim()?.takeIf { it.isNotEmpty() }
        _state.update {
            it.copy(
                isLoading = false,
                items = items.map(::toUi),
                isEmpty = items.isEmpty(),
                correction = correction,
                isCorrected = result.isCorrectedSearch,
                notices = notices(result.metaInfo),
                hasMore = Page.isValid(nextPage)
            )
        }
        loadExtras(items)
    }

    private fun handleNextItems(result: ListExtractor.InfoItemsPage<InfoItem>, page: Page?) {
        if (result.errors.isNotEmpty()) {
            emitError(
                ErrorInfo(
                    result.errors,
                    UserAction.SEARCHED,
                    "\"$searchString\" → pageUrl: ${page?.url}, pageIds: ${page?.ids}",
                    serviceId,
                    browserUrl()
                )
            )
        }
        nextPage = result.nextPage
        val items = result.items.filter(::isSupported)
        rawItems = rawItems + items
        _state.update {
            it.copy(
                isLoadingMore = false,
                items = it.items + items.map(::toUi),
                hasMore = Page.isValid(nextPage)
            )
        }
        loadExtras(items)
    }

    private fun handleSearchError(error: Throwable) {
        if (error is SearchExtractor.NothingFoundException) {
            _state.update { it.copy(isLoading = false, isEmpty = true) }
            return
        }
        val info = ErrorInfo(error, UserAction.SEARCHED, searchString, serviceId, browserUrl())
        lastError = info
        val actionLabel = when {
            info.recaptchaUrl != null -> app.getString(R.string.recaptcha_solve)
            info.isReportable -> app.getString(R.string.error_snackbar_action)
            else -> null
        }
        _state.update {
            it.copy(
                isLoading = false,
                error = SearchError(
                    message = info.getMessage(app).toString(),
                    actionLabel = actionLabel,
                    canRetry = info.isRetryable,
                    canOpenInBrowser = info.openInBrowserUrl != null
                )
            )
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Subscriptions and watch progress                                                          */
    /* ---------------------------------------------------------------------------------------- */

    fun toggleSubscription(item: YpChannelItem) {
        val subscribe = _state.value.subscribed[item.url] != true
        val channel = channelItemFor(item)
        val action = if (subscribe) {
            val entity = SubscriptionEntity(
                serviceId = item.serviceId,
                url = item.url,
                name = item.name,
                avatarUrl = channel?.let { ImageStrategy.imageListToDbUrl(it.thumbnails) },
                subscriberCount = channel?.subscriberCount?.takeIf { it >= 0 },
                description = channel?.description
            )
            Completable.fromAction { subscriptionManager.insertSubscription(entity) }
        } else {
            Completable.fromAction {
                subscriptionManager.subscriptionTable().deleteSubscription(item.serviceId, item.url)
            }
        }
        setSubscribed(item.url, subscribe)
        disposables.add(
            action.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { setSubscribed(item.url, !subscribe) })
        )
    }

    private fun setSubscribed(url: String, subscribed: Boolean) {
        _state.update { it.copy(subscribed = it.subscribed + (url to subscribed)) }
    }

    private fun loadExtras(items: List<InfoItem>) {
        val channels = items.filterIsInstance<ChannelInfoItem>()
        if (channels.isNotEmpty()) {
            disposables.add(
                Single.fromCallable {
                    channels.associate { channel ->
                        channel.url to subscriptionManager.subscriptionTable()
                            .getSubscriptionFlowable(channel.serviceId, channel.url)
                            .blockingFirst()
                            .isNotEmpty()
                    }
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { found -> _state.update { it.copy(subscribed = it.subscribed + found) } },
                        { }
                    )
            )
        }

        val streams = items.filterIsInstance<StreamInfoItem>().filter { it.duration > 0 }
        if (streams.isNotEmpty() && DependentPreferenceHelper.getPositionsInListsEnabled(app)) {
            disposables.add(
                Single.fromCallable {
                    streams.mapNotNull { stream ->
                        historyRecordManager.loadStreamState(stream).blockingGet()?.let { state ->
                            val fraction = state.progressMillis / 1000f / stream.duration
                            stream.url to fraction.coerceIn(0f, 1f)
                        }
                    }.toMap()
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { progress -> applyProgress(progress) },
                        { }
                    )
            )
        }
    }

    private fun applyProgress(progress: Map<String, Float>) {
        if (progress.isEmpty()) {
            return
        }
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    val fraction = progress[item.url]
                    if (item is YpStreamItem && fraction != null) item.copy(progress = fraction) else item
                }
            )
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Mapping                                                                                   */
    /* ---------------------------------------------------------------------------------------- */

    private fun isSupported(item: InfoItem) = item is StreamInfoItem || item is ChannelInfoItem || item is PlaylistInfoItem

    private fun toUi(item: InfoItem): YpListItem = when (item) {
        is StreamInfoItem -> {
            val live = StreamTypeUtil.isLiveStream(item.streamType)
            YpStreamItem(
                serviceId = item.serviceId,
                url = item.url,
                title = item.name,
                uploaderName = item.uploaderName.orEmpty(),
                thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
                durationText = when {
                    item.duration > 0 -> Localization.getDurationString(item.duration)
                    live -> app.getString(R.string.duration_live)
                    else -> null
                },
                isLive = live,
                details = streamDetails(item)
            )
        }

        is ChannelInfoItem -> YpChannelItem(
            serviceId = item.serviceId,
            url = item.url,
            name = item.name,
            avatarUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
            description = item.description?.takeIf { it.isNotBlank() },
            details = channelDetails(item)
        )

        else -> {
            val playlist = item as PlaylistInfoItem
            YpPlaylistItem(
                serviceId = playlist.serviceId,
                url = playlist.url,
                name = playlist.name,
                thumbnailUrl = ImageStrategy.choosePreferredImage(playlist.thumbnails),
                uploaderName = playlist.uploaderName,
                countText = Localization.localizeStreamCountMini(app, playlist.streamCount)
            )
        }
    }

    private fun streamDetails(item: StreamInfoItem): String {
        val views = when {
            item.viewCount < 0 -> ""

            item.streamType == StreamType.AUDIO_LIVE_STREAM ->
                Localization.listeningCount(app, item.viewCount)

            item.streamType == StreamType.LIVE_STREAM ->
                Localization.shortWatchingCount(app, item.viewCount)

            else -> Localization.shortViewCount(app, item.viewCount)
        }
        val date = Localization
            .relativeTimeOrTextual(app, item.uploadDate, item.textualUploadDate)
            .orEmpty()
        return when {
            date.isEmpty() -> views
            views.isEmpty() -> date
            else -> Localization.concatenateStrings(views, date)
        }
    }

    private fun channelDetails(item: ChannelInfoItem): String? {
        val subscribers = item.subscriberCount.takeIf { it >= 0 }
            ?.let { Localization.shortSubscriberCount(app, it) }
        val streams = item.streamCount.takeIf { it >= 0 }
            ?.let { Localization.localizeStreamCount(app, it) }
        return when {
            subscribers != null && streams != null ->
                Localization.concatenateStrings(subscribers, streams)

            else -> subscribers ?: streams
        }
    }

    private fun notices(metaInfo: List<MetaInfo>): List<SearchNotice> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        if (!prefs.getBoolean(app.getString(R.string.show_meta_info_key), true)) {
            return emptyList()
        }
        return metaInfo.map { info ->
            SearchNotice(
                title = info.title?.takeIf { it.isNotBlank() },
                text = HtmlCompat.fromHtml(info.content.content(), HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .trim(),
                url = info.urls.firstOrNull()?.toString()
            )
        }
    }

    private fun isAllFilter() = contentFilter.isEmpty() || contentFilter[0] == "all"

    private fun selectedFilterIndex() = if (contentFilter.isEmpty()) 0 else filters.indexOf(contentFilter[0]).coerceAtLeast(0)

    private fun hint(): String {
        val name = service?.serviceInfo?.name ?: return ""
        return if (isAllFilter()) {
            app.getString(R.string.search_with_service_name, name)
        } else {
            app.getString(
                R.string.search_with_service_name_and_filter,
                name,
                ServiceHelper.getTranslatedFilterString(contentFilter[0], app)
            )
        }
    }

    private fun emitError(errorInfo: ErrorInfo) {
        _events.tryEmit(SearchEvent.ShowError(errorInfo))
    }

    override fun onCleared() {
        searchDisposable?.dispose()
        suggestionDisposable?.dispose()
        disposables.dispose()
    }

    private companion object {
        /** Remote suggestions are only fetched for queries at least this long. */
        const val THRESHOLD_NETWORK_SUGGESTION = 1

        /** Wait after the last key before fetching suggestions, in milliseconds. */
        const val SUGGESTIONS_DEBOUNCE = 120L
    }
}
