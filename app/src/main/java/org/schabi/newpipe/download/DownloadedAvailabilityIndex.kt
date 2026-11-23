/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.net.URI
import java.util.concurrent.Executors
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import us.shandian.giga.get.sqlite.FinishedMissionStore

/**
 * Cached offline-video identities shared across all stream cards and filters.
 * Finished database records alone are insufficient: only locally verified video files count.
 * Never read the mission database from a RecyclerView item bind.
 */
object DownloadedAvailabilityIndex {
    private const val MIN_REFRESH_INTERVAL_MS = 30_000L
    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "download-availability").apply { isDaemon = true }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val changes = MutableLiveData(0L)
    private var sequence = 0L
    private var refreshing = false
    private var requestedAgain = false
    private var lastRefresh = 0L
    private var loaded = false

    @Volatile
    private var availableKeys: Set<String> = emptySet()

    val updates: LiveData<Long> get() = changes

    @JvmStatic
    fun isAvailable(serviceId: Int, url: String): Boolean {
        return identity(serviceId, url)?.let { it in availableKeys } == true
    }

    @JvmStatic
    @JvmOverloads
    fun requestRefresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        synchronized(lock) {
            if (refreshing) {
                if (force) requestedAgain = true
                return
            }
            if (!force && loaded &&
                SystemClock.elapsedRealtime() - lastRefresh < MIN_REFRESH_INTERVAL_MS
            ) {
                return
            }
            refreshing = true
        }
        worker.execute {
            val fresh = runCatching {
                val store = FinishedMissionStore(app)
                try {
                    store.loadFinishedMissions().asSequence().mapNotNull { mission ->
                        val source = mission.source.orEmpty()
                        val available = runCatching {
                            val storage = mission.storage
                            storage != null && isCompleteVideo(
                                mission.kind,
                                mission.length,
                                storage.existsAsFile(),
                                storage.length(),
                                source
                            )
                        }.getOrDefault(false)
                        if (!available) return@mapNotNull null
                        val serviceId = runCatching {
                            NewPipe.getServiceByUrl(source).serviceId
                        }.getOrNull() ?: return@mapNotNull null
                        identity(serviceId, source)
                    }.toSet()
                } finally {
                    store.close()
                }
            }.getOrDefault(emptySet())
            mainHandler.post {
                val changed = fresh != availableKeys
                availableKeys = fresh
                val rerun: Boolean
                synchronized(lock) {
                    loaded = true
                    lastRefresh = SystemClock.elapsedRealtime()
                    refreshing = false
                    rerun = requestedAgain
                    requestedAgain = false
                }
                if (changed) changes.value = ++sequence
                if (rerun) requestRefresh(app, force = true)
            }
        }
    }

    /** Invalidate immediately after a completed mission is inserted or removed. */
    @JvmStatic
    fun invalidate(context: Context) {
        requestRefresh(context, force = true)
    }

    internal fun isCompleteVideo(
        kind: Char,
        expectedBytes: Long,
        exists: Boolean,
        actualBytes: Long,
        source: String
    ): Boolean {
        return kind == 'v' && expectedBytes > 0 && exists && actualBytes >= expectedBytes &&
            source.isNotBlank() && !source.contains("wizestream-segments=")
    }

    internal fun identity(serviceId: Int, url: String): String? {
        if (url.isBlank() || url.contains("wizestream-segments=")) return null
        return runCatching {
            if (serviceId == ServiceList.YouTube.serviceId) {
                YoutubeStreamLinkHandlerFactory.getInstance().getId(url)
                    ?.takeIf { it.isNotBlank() }?.let { "youtube:" + it }
            } else {
                "service:" + serviceId + ":" + URI(url).normalize().toString()
            }
        }.getOrNull()
    }
}
