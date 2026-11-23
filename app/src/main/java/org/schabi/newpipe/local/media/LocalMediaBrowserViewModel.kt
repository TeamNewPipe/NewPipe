/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class LocalMediaBrowserState(
    val location: LocalMediaDocumentLocation? = null,
    val title: String = "",
    val entries: List<LocalMediaDocumentEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isUnavailable: Boolean = false
)

class LocalMediaBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val browser = LocalMediaDocumentBrowser(application)
    private val store = LocalMediaTreeStore(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val mutableState = MutableLiveData(LocalMediaBrowserState())

    val state: LiveData<LocalMediaBrowserState> = mutableState

    init {
        showRoots()
    }

    fun showRoots() {
        load(null, "") { browser.roots(store.roots()) }
    }

    fun open(entry: LocalMediaDocumentEntry) {
        if (!entry.isDirectory || !entry.isAvailable) return
        load(entry.location, entry.name) { browser.list(entry.location) }
    }

    fun goBack(): Boolean {
        val current = mutableState.value?.location ?: return false
        if (current.path.isEmpty()) {
            showRoots()
        } else {
            val parent = current.copy(path = current.path.dropLast(1))
            val title = parent.path.lastOrNull().orEmpty().ifBlank { rootName(current.rootUri) }
            load(parent, title) { browser.list(parent) }
        }
        return true
    }

    fun addRoot(uri: Uri) {
        store.add(uri)
        val entry = browser.roots(setOf(uri.toString())).firstOrNull()
        if (entry == null || !entry.isAvailable) showRoots() else open(entry)
    }

    fun removeRoot(rootUri: String) {
        store.remove(rootUri)
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(rootUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        showRoots()
    }

    fun refresh() {
        LocalMediaMetadataLoader.invalidateCache()
        val current = mutableState.value ?: LocalMediaBrowserState()
        val location = current.location
        if (location == null) {
            showRoots()
        } else {
            load(location, current.title) { browser.list(location) }
        }
    }

    fun mediaItem(entry: LocalMediaDocumentEntry): LocalMediaItem? = browser.mediaItem(entry)

    /** Resolve embedded tags before a SAF file becomes a playback queue item. */
    fun resolveMediaItem(
        entry: LocalMediaDocumentEntry,
        onReady: (LocalMediaItem?) -> Unit
    ) {
        scanExecutor.execute {
            val item = browser.mediaItem(entry)?.let { unresolved ->
                unresolved.withEmbeddedMetadata(
                    LocalMediaMetadataLoader.readCached(
                        getApplication<Application>().contentResolver,
                        unresolved.contentUri,
                        unresolved.mimeType
                    )
                )
            }
            mainHandler.post { onReady(item) }
        }
    }

    fun collect(
        location: LocalMediaDocumentLocation,
        onReady: (List<LocalMediaItem>) -> Unit
    ) {
        scanExecutor.execute {
            val items = browser.collectMedia(location)
            mainHandler.post { onReady(items) }
        }
    }

    private fun load(
        location: LocalMediaDocumentLocation?,
        title: String,
        query: () -> List<LocalMediaDocumentEntry>
    ) {
        val requestedGeneration = generation.incrementAndGet()
        mutableState.value = LocalMediaBrowserState(
            location = location,
            title = title,
            entries = mutableState.value?.entries.orEmpty(),
            isLoading = true
        )
        executor.execute {
            val entries = runCatching(query).getOrDefault(emptyList())
            if (generation.get() == requestedGeneration) {
                mutableState.postValue(
                    LocalMediaBrowserState(
                        location = location,
                        title = title,
                        entries = entries,
                        isUnavailable = location != null && entries.isEmpty() &&
                            !isLocationAvailable(location)
                    )
                )
            }
        }
    }

    private fun rootName(rootUri: String): String = browser.roots(setOf(rootUri))
        .firstOrNull()
        ?.name
        .orEmpty()

    private fun isLocationAvailable(location: LocalMediaDocumentLocation): Boolean {
        return browser.isAvailable(location)
    }

    override fun onCleared() {
        generation.incrementAndGet()
        executor.shutdownNow()
        scanExecutor.shutdownNow()
        super.onCleared()
    }
}
