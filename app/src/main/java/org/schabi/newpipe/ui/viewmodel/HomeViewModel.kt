package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ExtractorHelper

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NewPipeDatabase.getInstance(application)

    private val _items = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val items: StateFlow<List<StreamInfoItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentKioskName = MutableStateFlow("")
    val currentKioskName: StateFlow<String> = _currentKioskName.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _dynamicCategories = MutableStateFlow<List<String>>(listOf("All", "Music", "Gaming", "News", "Movies"))
    val dynamicCategories: StateFlow<List<String>> = _dynamicCategories.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var nextPage: org.schabi.newpipe.extractor.Page? = null
    private var currentServiceId: Int = -1
    private var currentKioskUrl: String = ""

    // In-memory cache per category (cleared on pull-to-refresh or app launch for 'All' to ensure fresh feed)
    private val categoryCache = mutableMapOf<String, List<StreamInfoItem>>()

    fun loadHome(serviceId: Int, category: String = "All", force: Boolean = false) {
        if (!force && _items.value.isNotEmpty() && currentServiceId == serviceId && _selectedCategory.value == category) return

        currentServiceId = serviceId
        _selectedCategory.value = category

        // If cached and not forced (and not 'All' for refresh), display instantly
        if (!force && category != "All" && categoryCache.containsKey(category)) {
            _items.value = categoryCache[category] ?: emptyList()
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            if (force) {
                _items.value = emptyList()
                categoryCache.clear()
            }
            nextPage = null

            try {
                // 1. Fetch watch & search history from Room Database
                val (watchHistory, searchHistory) = withContext(Dispatchers.IO) {
                    val wHistory = try {
                        database.streamHistoryDAO().getHistory().firstOrNull() ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val sHistory = try {
                        database.searchHistoryDAO().getUniqueEntries(12).firstOrNull() ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    Pair(wHistory, sHistory)
                }

                // 2. Compute dynamic category filter chips (Top watched channels + top searches + defaults)
                withContext(Dispatchers.Default) {
                    val topChannels = watchHistory.mapNotNull { it.streamEntity.uploader }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(4)
                    val topSearches = searchHistory.filter { it.isNotBlank() }.take(3)
                    val defaultCategories = listOf("Music", "Gaming", "News", "Movies")
                    _dynamicCategories.value = (listOf("All") + topChannels + topSearches + defaultCategories).distinct()
                }

                if (category == "All") {
                    // STEP 1: Fast initial load of Trending Kiosk
                    val (trendingItems, kioskNextPage) = withContext(Dispatchers.IO) {
                        val service = NewPipe.getService(serviceId)
                        val defaultId = service.kioskList.defaultKioskId
                        currentKioskUrl = service.kioskList.getListLinkHandlerFactoryByType(defaultId).fromId(defaultId).url

                        val kioskInfo: KioskInfo = ExtractorHelper.getKioskInfo(serviceId, currentKioskUrl, force)
                        val items = (kioskInfo.getRelatedItems() ?: emptyList()).filterIsInstance<StreamInfoItem>()
                        Pair(items, kioskInfo.nextPage)
                    }

                    nextPage = kioskNextPage
                    // Show initial baseline so user never sees an empty screen
                    if (_items.value.isEmpty()) {
                        _items.value = trendingItems
                    }
                    _isLoading.value = false

                    // STEP 2: Intelligent Multi-Source Personalization & Fair Interleaved Mixing
                    withContext(Dispatchers.IO) {
                        try {
                            val service = NewPipe.getService(serviceId)

                            // Pick randomized sample queries from search history
                            val sampledSearches = searchHistory
                                .filter { it.isNotBlank() }
                                .shuffled()
                                .take(3)

                            // Pick randomized sample channels from watch history
                            val sampledCreators = watchHistory
                                .mapNotNull { it.streamEntity.uploader }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .shuffled()
                                .take(3)

                            // Extract title keywords from recently watched videos (e.g. key topics)
                            val sampledTopics = watchHistory
                                .map { it.streamEntity.title.orEmpty() }
                                .filter { it.isNotBlank() }
                                .shuffled()
                                .take(2)
                                .map { rawTitle ->
                                    // Clean up title (remove emoji, brackets, etc. to get core keywords)
                                    rawTitle.replace(Regex("[\\[\\]()|#]"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                        .take(30)
                                }
                                .filter { it.length >= 3 }

                            val querySeeds = (sampledSearches + sampledCreators + sampledTopics).distinct().shuffled().take(5)

                            // Concurrently fetch video pools for all sampled query seeds
                            val searchPools: List<List<StreamInfoItem>> = coroutineScope {
                                querySeeds.map { query ->
                                    async {
                                        try {
                                            val searchInfo = SearchInfo.getInfo(
                                                service,
                                                service.searchQHFactory.fromQuery(query, emptyList<String>(), null)
                                            )
                                            (searchInfo.getRelatedItems() ?: emptyList()).filterIsInstance<StreamInfoItem>()
                                        } catch (_: Exception) {
                                            emptyList<StreamInfoItem>()
                                        }
                                    }
                                }.awaitAll()
                            }

                            // If user is brand new (no watch/search history), fetch alternate kiosk categories (Music, Gaming, Movies) for rich mix
                            val fallbackPools: List<List<StreamInfoItem>> = if (querySeeds.isEmpty()) {
                                coroutineScope {
                                    listOf("trending_music", "trending_gaming", "trending_movies_and_shows").map { kId ->
                                        async {
                                            try {
                                                val kUrl = service.kioskList.getListLinkHandlerFactoryByType(kId).fromId(kId).url
                                                val kInfo: KioskInfo = ExtractorHelper.getKioskInfo(serviceId, kUrl, false)
                                                (kInfo.getRelatedItems() ?: emptyList()).filterIsInstance<StreamInfoItem>()
                                            } catch (_: Exception) {
                                                emptyList<StreamInfoItem>()
                                            }
                                        }
                                    }.awaitAll()
                                }
                            } else {
                                emptyList()
                            }

                            // STEP 3: Multi-Source Interleaved Fair Mixing on Dispatchers.Default
                            val finalMixedList = withContext(Dispatchers.Default) {
                                val allPools = mutableListOf<List<StreamInfoItem>>()
                                searchPools.filter { it.isNotEmpty() }.forEach { allPools.add(it) }
                                fallbackPools.filter { it.isNotEmpty() }.forEach { allPools.add(it) }
                                if (trendingItems.isNotEmpty()) {
                                    allPools.add(trendingItems.shuffled())
                                }

                                val mixedList = mutableListOf<StreamInfoItem>()
                                val seenUrls = mutableSetOf<String>()
                                val watchedUrls = watchHistory.map { it.streamEntity.url }.toSet()

                                val maxLen = allPools.maxOfOrNull { it.size } ?: 0

                                for (round in 0 until maxLen) {
                                    // Shuffle the pool order per round to produce a natural mixed order
                                    for (pool in allPools.shuffled()) {
                                        if (round < pool.size) {
                                            val item = pool[round]
                                            // Prioritize unwatched videos
                                            if (!seenUrls.contains(item.url) && !watchedUrls.contains(item.url)) {
                                                seenUrls.add(item.url)
                                                mixedList.add(item)
                                            }
                                        }
                                    }
                                }

                                // Fallback pass: append remaining items if list is small
                                for (pool in allPools) {
                                    for (item in pool) {
                                        if (!seenUrls.contains(item.url)) {
                                            seenUrls.add(item.url)
                                            mixedList.add(item)
                                        }
                                    }
                                }

                                mixedList
                            }

                            if (finalMixedList.isNotEmpty()) {
                                withContext(Dispatchers.Main.immediate) {
                                    _items.value = finalMixedList
                                    categoryCache[category] = finalMixedList
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            e.printStackTrace()
                        }
                    }
                } else if (category in listOf("Music", "Gaming", "Movies", "News")) {
                    val (items, name, page) = withContext(Dispatchers.IO) {
                        val service = NewPipe.getService(serviceId)
                        val kioskId = when (category) {
                            "Music" -> "trending_music"
                            "Gaming" -> "trending_gaming"
                            "Movies" -> "trending_movies_and_shows"
                            else -> service.kioskList.defaultKioskId
                        }
                        currentKioskUrl = try {
                            service.kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).url
                        } catch (e: Exception) {
                            val defaultId = service.kioskList.defaultKioskId
                            service.kioskList.getListLinkHandlerFactoryByType(defaultId).fromId(defaultId).url
                        }
                        val info: KioskInfo = ExtractorHelper.getKioskInfo(serviceId, currentKioskUrl, force)
                        val list = (info.getRelatedItems() ?: emptyList()).filterIsInstance<StreamInfoItem>()
                        Triple(list, info.name ?: category, info.nextPage)
                    }
                    _currentKioskName.value = name
                    _items.value = items
                    categoryCache[category] = items
                    nextPage = page
                    _isLoading.value = false
                } else {
                    // Dynamic creator / topic search
                    val (items, page) = withContext(Dispatchers.IO) {
                        val service = NewPipe.getService(serviceId)
                        val searchInfo = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(category, emptyList<String>(), null))
                        val list = (searchInfo.getRelatedItems() ?: emptyList()).filterIsInstance<StreamInfoItem>()
                        Pair(list, searchInfo.nextPage)
                    }
                    _currentKioskName.value = category
                    _items.value = items
                    categoryCache[category] = items
                    nextPage = page
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreItems() {
        val page = nextPage ?: return
        if (_isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    ExtractorHelper.getMoreKioskItems(currentServiceId, currentKioskUrl, page)
                }
                val newItems = withContext(Dispatchers.Default) {
                    val seenUrls = _items.value.map { it.url }.toSet()
                    val filtered = result.items.filter { !seenUrls.contains(it.url) }
                    _items.value + filtered
                }
                _items.value = newItems
                nextPage = result.nextPage
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
