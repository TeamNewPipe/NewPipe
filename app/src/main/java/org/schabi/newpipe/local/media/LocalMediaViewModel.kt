/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LocalMediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalMediaRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val mutableState = MutableLiveData(LocalMediaLibraryState(isLoading = false))
    private var currentAccess: LocalMediaAccess? = null

    val state: LiveData<LocalMediaLibraryState> = mutableState

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            currentAccess?.let { load(it, force = true) }
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
        application.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }

    fun refresh() {
        currentAccess?.let { load(it, force = true) }
    }

    fun load(access: LocalMediaAccess, force: Boolean = false) {
        if (force) LocalMediaMetadataLoader.invalidateCache()
        if (!access.hasAnyAccess) {
            currentAccess = access
            mutableState.value = LocalMediaLibraryState(isLoading = false)
            return
        }
        if (
            !force && currentAccess == access &&
            mutableState.value?.library != LocalMediaLibrary.EMPTY
        ) {
            return
        }

        currentAccess = access
        val requestedGeneration = generation.incrementAndGet()
        mutableState.value = LocalMediaLibraryState(
            isLoading = true,
            library = mutableState.value?.library ?: LocalMediaLibrary.EMPTY
        )
        executor.execute {
            val library = repository.query(access)
            if (generation.get() == requestedGeneration) {
                mutableState.postValue(
                    LocalMediaLibraryState(isLoading = false, library = library)
                )
            }
        }
    }

    override fun onCleared() {
        generation.incrementAndGet()
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
        executor.shutdownNow()
        super.onCleared()
    }
}
