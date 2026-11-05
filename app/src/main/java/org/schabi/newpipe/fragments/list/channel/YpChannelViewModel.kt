/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import io.github.yewplay.core.model.YpListItem
import io.github.yewplay.feature.channel.ChannelAbout
import io.github.yewplay.feature.channel.ChannelHeader
import io.github.yewplay.feature.channel.ChannelTab
import io.github.yewplay.feature.channel.ChannelTabContent
import io.github.yewplay.feature.channel.ChannelUiState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.yewplay.YpItems
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy

/** One-off things the channel screen asks of the fragment. */
sealed interface ChannelEvent {
    /** A non-fatal error, shown as a snackbar. */
    data class ShowError(val errorInfo: ErrorInfo) : ChannelEvent

    /** Just subscribed: offer the new-video notifications (as before). */
    data object Subscribed : ChannelEvent
}

/**
 * YewPlay: state of the Compose channel screen. Loading, tabs, pagination, subscription and
 * notifications are NewPipe's, as in the previous ChannelFragment and ChannelTabFragment.
 */
class YpChannelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val subscriptionManager = SubscriptionManager(application)
    private val historyRecordManager = HistoryRecordManager(application)
    private val disposables = CompositeDisposable()
    private val tabDisposables = CompositeDisposable()
    private var infoDisposable: Disposable? = null
    private var subscriptionDisposable: Disposable? = null

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ChannelEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChannelEvent> = _events

    var serviceId: Int = 0
        private set
    var url: String = ""
        private set

    /** The loaded channel, for share, browser, RSS and the parent channel. */
    var info: ChannelInfo? = null
        private set

    /** The error shown in place of the content, for its buttons. */
    var lastError: ErrorInfo? = null
        private set

    /** Link handler of each tab; null for "About". */
    private var tabHandlers: List<ListLinkHandler?> = emptyList()
    private val rawItems = mutableMapOf<Int, List<InfoItem>>()
    private val nextPages = mutableMapOf<Int, Page?>()
    private var subscription: SubscriptionEntity? = null
    private var subscriptionKnown = false
    private var initialized = false

    /** Starts the screen once; later calls (e.g. after a rotation) do nothing. */
    fun init(serviceId: Int, url: String, name: String, frontPage: Boolean, selectedTab: Int) {
        if (initialized) {
            return
        }
        initialized = true
        this.serviceId = serviceId
        this.url = url
        _state.update {
            it.copy(title = name, showBack = !frontPage, selectedTab = selectedTab.coerceAtLeast(0))
        }
        load(forceLoad = false)
    }

    fun retry() {
        val tab = _state.value.selectedTab
        if (info == null) {
            load(forceLoad = true)
        } else if (_state.value.tabContent[tab]?.error != null) {
            loadTab(tab, forceLoad = true)
        }
    }

    private fun load(forceLoad: Boolean) {
        lastError = null
        _state.update { it.copy(isLoading = true, error = null) }
        infoDisposable?.dispose()
        infoDisposable = ExtractorHelper.getChannelInfo(serviceId, url, forceLoad)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleInfo) { error ->
                val errorInfo = ErrorInfo(error, UserAction.REQUESTED_CHANNEL, url, serviceId, url)
                lastError = errorInfo
                _state.update { it.copy(isLoading = false, error = YpItems.errorUi(app, errorInfo)) }
            }
    }

    private fun handleInfo(result: ChannelInfo) {
        info = result
        serviceId = result.serviceId
        url = result.originalUrl ?: url
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)

        val handlers = mutableListOf<ListLinkHandler?>()
        val tabs = mutableListOf<ChannelTab>()
        for (handler in result.tabs) {
            val tab = handler.contentFilters.firstOrNull() ?: continue
            if (ChannelTabHelper.showChannelTab(app, prefs, tab)) {
                handlers.add(handler)
                tabs.add(
                    ChannelTab(
                        label = app.getString(ChannelTabHelper.getTranslationKey(tab)),
                        isStreams = ChannelTabHelper.isStreamsTab(tab),
                        isAbout = false
                    )
                )
            }
        }
        if (ChannelTabHelper.showChannelTab(app, prefs, R.string.show_channel_tabs_about)) {
            handlers.add(null)
            tabs.add(ChannelTab(app.getString(R.string.channel_tab_about), false, isAbout = true))
        }
        tabHandlers = handlers
        rawItems.clear()
        nextPages.clear()
        tabDisposables.clear()

        val selected = _state.value.selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                isLoading = false,
                error = null,
                header = header(result),
                contentNotSupported = result.errors.any { e -> e is ContentNotSupportedException },
                hasRss = !result.feedUrl.isNullOrEmpty(),
                tabs = tabs,
                selectedTab = selected,
                tabContent = emptyMap(),
                about = about(result)
            )
        }

        // keep the stored subscription up to date, as before
        disposables.add(
            subscriptionManager.updateChannelInfo(result)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    {
                        emitError(
                            ErrorInfo(
                                it,
                                UserAction.SUBSCRIPTION_UPDATE,
                                "Updating subscription for ${result.url}",
                                result
                            )
                        )
                    }
                )
        )
        monitorSubscription(result)
        selectTab(selected)
    }

    private fun header(result: ChannelInfo): ChannelHeader {
        val banner = if (ImageStrategy.shouldLoadImages() && result.banners.isNotEmpty()) {
            ImageStrategy.choosePreferredImage(result.banners)
        } else {
            null
        }
        val parentName = result.parentChannelName
        return ChannelHeader(
            name = result.name,
            avatarUrl = ImageStrategy.choosePreferredImage(result.avatars),
            bannerUrl = banner,
            details = if (result.subscriberCount >= 0) {
                Localization.shortSubscriberCount(app, result.subscriberCount)
            } else {
                app.getString(R.string.subscribers_count_not_available)
            },
            description = result.description?.trim()?.takeIf { it.isNotEmpty() },
            parentText = parentName?.takeIf { it.isNotEmpty() }
                ?.let { app.getString(R.string.channel_created_by, it) },
            parentAvatarUrl = ImageStrategy.choosePreferredImage(result.parentChannelAvatars)
        )
    }

    private fun about(result: ChannelInfo): ChannelAbout {
        val subscribers = if (result.subscriberCount != StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT) {
            app.getString(R.string.metadata_subscribers) to
                Localization.localizeNumber(result.subscriberCount)
        } else {
            null
        }
        val images = listOfNotNull(
            largest(result.avatars)?.let { app.getString(R.string.metadata_avatars) to it },
            largest(result.banners)?.let { app.getString(R.string.metadata_banners) to it }
        )
        return ChannelAbout(
            description = result.description?.trim()?.takeIf { it.isNotEmpty() },
            subscribers = subscribers,
            tags = result.tags.orEmpty(),
            images = images
        )
    }

    private fun largest(images: List<Image>): String? =
        images.maxByOrNull { it.width.toLong() * it.height }?.url

    /* ---------------------------------------------------------------------------------------- */
    /* Subscription and notifications                                                            */
    /* ---------------------------------------------------------------------------------------- */

    private fun monitorSubscription(result: ChannelInfo) {
        subscriptionDisposable?.dispose()
        subscriptionKnown = false
        subscriptionDisposable = subscriptionManager.subscriptionTable()
            .getSubscriptionFlowable(result.serviceId, result.url)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { entities ->
                    val wasSubscribed = subscription != null
                    subscription = entities.firstOrNull()
                    val subscribed = subscription != null
                    val notificationsEnabled =
                        NotificationHelper.areNewStreamsNotificationsEnabled(app)
                    if (subscriptionKnown && !wasSubscribed && subscribed && notificationsEnabled) {
                        _events.tryEmit(ChannelEvent.Subscribed)
                    }
                    subscriptionKnown = true
                    _state.update {
                        it.copy(
                            subscribed = subscribed,
                            notifyVisible = subscribed,
                            notifyEnabled = notificationsEnabled,
                            notifyOn = subscription?.notificationMode == NotificationMode.ENABLED
                        )
                    }
                },
                {
                    emitError(
                        ErrorInfo(it, UserAction.SUBSCRIPTION_GET, "Get subscription status", result)
                    )
                }
            )
    }

    fun toggleSubscription() {
        val result = info ?: return
        val current = subscription
        val action = if (current != null) {
            Completable.fromAction { subscriptionManager.deleteSubscription(current) }
        } else {
            val entity = SubscriptionEntity(
                serviceId = result.serviceId,
                url = result.url,
                name = result.name,
                avatarUrl = ImageStrategy.imageListToDbUrl(result.avatars),
                subscriberCount = result.subscriberCount,
                description = result.description
            )
            Completable.fromAction { subscriptionManager.insertSubscription(entity) }
        }
        disposables.add(
            action.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { },
                    {
                        emitError(
                            ErrorInfo(
                                it,
                                UserAction.SUBSCRIPTION_CHANGE,
                                "Changing subscription for ${result.url}",
                                result
                            )
                        )
                    }
                )
        )
    }

    fun toggleNotify() {
        setNotify(!_state.value.notifyOn)
    }

    fun setNotify(enabled: Boolean) {
        val result = info ?: return
        disposables.add(
            subscriptionManager.updateNotificationMode(
                result.serviceId,
                result.url,
                if (enabled) NotificationMode.ENABLED else NotificationMode.DISABLED
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { })
        )
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Tabs                                                                                      */
    /* ---------------------------------------------------------------------------------------- */

    fun selectTab(index: Int) {
        if (index !in tabHandlers.indices) {
            return
        }
        _state.update { it.copy(selectedTab = index) }
        if (tabHandlers[index] != null && _state.value.tabContent[index] == null) {
            loadTab(index, forceLoad = false)
        }
    }

    private fun loadTab(index: Int, forceLoad: Boolean) {
        val handler = tabHandlers.getOrNull(index) ?: return
        setTab(index, ChannelTabContent(isLoading = true))
        tabDisposables.add(
            ExtractorHelper.getChannelTab(serviceId, handler, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result -> handleTab(index, result) },
                    { error ->
                        val errorInfo =
                            ErrorInfo(error, UserAction.REQUESTED_CHANNEL, url, serviceId, url)
                        lastError = errorInfo
                        setTab(
                            index,
                            ChannelTabContent(
                                isLoading = false,
                                error = YpItems.errorUi(app, errorInfo)
                            )
                        )
                    }
                )
        )
    }

    private fun handleTab(index: Int, result: ChannelTabInfo) {
        val errors = result.errors.filter { it !is ContentNotSupportedException }
        if (errors.isNotEmpty()) {
            emitError(ErrorInfo(errors, UserAction.REQUESTED_CHANNEL, url, serviceId, url))
        }
        val items = result.relatedItems.filter(YpItems::isSupported)
        rawItems[index] = items
        nextPages[index] = result.nextPage
        setTab(
            index,
            ChannelTabContent(
                items = items.map { toUi(it) },
                isLoading = false,
                hasMore = Page.isValid(result.nextPage),
                isEmpty = items.isEmpty()
            )
        )
        loadProgress(index, items)
    }

    fun loadMore() {
        val index = _state.value.selectedTab
        val handler = tabHandlers.getOrNull(index) ?: return
        val page = nextPages[index]
        val content = _state.value.tabContent[index] ?: return
        if (!Page.isValid(page) || content.isLoading || content.isLoadingMore) {
            return
        }
        setTab(index, content.copy(isLoadingMore = true))
        tabDisposables.add(
            ExtractorHelper.getMoreChannelTabItems(serviceId, handler, page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result -> handleMore(index, result) },
                    { error ->
                        // keep what was loaded; stop asking for more pages
                        updateTab(index) { it.copy(isLoadingMore = false, hasMore = false) }
                        emitError(
                            ErrorInfo(error, UserAction.REQUESTED_CHANNEL, url, serviceId, url)
                        )
                    }
                )
        )
    }

    private fun handleMore(index: Int, result: ListExtractor.InfoItemsPage<InfoItem>) {
        if (result.errors.isNotEmpty()) {
            emitError(ErrorInfo(result.errors, UserAction.REQUESTED_CHANNEL, url, serviceId, url))
        }
        val items = result.items.filter(YpItems::isSupported)
        rawItems[index] = rawItems[index].orEmpty() + items
        nextPages[index] = result.nextPage
        updateTab(index) {
            it.copy(
                items = it.items + items.map { item -> toUi(item) },
                isLoadingMore = false,
                hasMore = Page.isValid(result.nextPage)
            )
        }
        loadProgress(index, items)
    }

    private fun loadProgress(index: Int, items: List<InfoItem>) {
        tabDisposables.add(
            YpItems.loadProgress(app, historyRecordManager, items)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { progress ->
                        updateTab(index) { it.copy(items = YpItems.withProgress(it.items, progress)) }
                    },
                    { }
                )
        )
    }

    private fun toUi(item: InfoItem): YpListItem = YpItems.toUi(app, item, showUploader = false)

    private fun setTab(index: Int, content: ChannelTabContent) {
        _state.update { it.copy(tabContent = it.tabContent + (index to content)) }
    }

    private fun updateTab(index: Int, change: (ChannelTabContent) -> ChannelTabContent) {
        _state.update { state ->
            val content = state.tabContent[index] ?: return@update state
            state.copy(tabContent = state.tabContent + (index to change(content)))
        }
    }

    /**
     * The videos of the selected tab as a queue that keeps loading the next pages.
     *
     * @return the queue, or null when the tab has no videos
     */
    fun playQueue(): PlayQueue? {
        val index = _state.value.selectedTab
        val handler = tabHandlers.getOrNull(index) ?: return null
        val streams = rawItems[index].orEmpty().filterIsInstance<StreamInfoItem>()
        if (streams.isEmpty()) {
            return null
        }
        return ChannelTabPlayQueue(serviceId, handler, nextPages[index], streams, 0)
    }

    /**
     * Finds the extractor item behind an item of the screen (for the item menu).
     *
     * @param item an item of the selected tab
     * @return the stream item, if it is a stream
     */
    fun streamItemFor(item: YpListItem): StreamInfoItem? =
        rawItems[_state.value.selectedTab].orEmpty()
            .firstOrNull { it.url == item.url && it.serviceId == item.serviceId } as? StreamInfoItem

    private fun emitError(errorInfo: ErrorInfo) {
        _events.tryEmit(ChannelEvent.ShowError(errorInfo))
    }

    override fun onCleared() {
        infoDisposable?.dispose()
        subscriptionDisposable?.dispose()
        tabDisposables.dispose()
        disposables.dispose()
    }
}
