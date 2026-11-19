/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.recommendations

import android.content.Context
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/**
 * Keeps recommendation model serialization off the main thread.
 * Events share one worker so an older write cannot overwrite a newer one.
 */
object RecommendationWorkDispatcher {
    private const val MAX_PENDING_ITEMS = 200
    private const val OBSERVATION_DELAY_MILLIS = 100L
    private const val TAG = "RecommendationWorker"

    private val worker = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "recommendations-worker").apply { priority = Thread.MIN_PRIORITY }
    }
    private val pendingLock = Any()
    private val pending = linkedMapOf<String, PendingObservations>()
    private var flushScheduled = false

    private data class PendingObservations(
        val context: Context,
        val profileId: String,
        val generation: Long,
        val items: LinkedHashMap<String, StreamInfoItem> = linkedMapOf()
    )

    @JvmStatic
    fun observe(context: Context, items: List<InfoItem>) {
        val selected = items.filterIsInstance<StreamInfoItem>().take(MAX_PENDING_ITEMS)
        if (selected.isEmpty()) return
        val app = context.applicationContext
        if (IncognitoMode.isEnabled(app)) return
        val profileId = ProfileManager.getActiveProfileId(app)
        val generation = generation(app, profileId)
        synchronized(pendingLock) {
            val batch = pending[profileId]?.takeIf { it.generation == generation }
                ?: PendingObservations(app, profileId, generation).also { pending[profileId] = it }
            selected.forEach { item ->
                val key = "${item.serviceId}:${item.url}"
                batch.items.remove(key)
                batch.items[key] = item
                if (batch.items.size > MAX_PENDING_ITEMS) {
                    batch.items.remove(batch.items.keys.first())
                }
            }
            if (!flushScheduled) {
                flushScheduled = true
                worker.schedule(::flushObservations, OBSERVATION_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            }
        }
    }

    @JvmStatic
    fun watched(context: Context, info: StreamInfo, seconds: Long, profileId: String) {
        dispatch(context, profileId) { LocalRecommendations.watched(it, info, seconds) }
    }

    @JvmStatic
    fun liked(context: Context, info: StreamInfo, liked: Boolean, profileId: String) {
        dispatch(context, profileId) { LocalRecommendations.liked(it, info, liked) }
    }

    @JvmStatic
    fun searched(context: Context, serviceId: Int, query: String, profileId: String) {
        dispatch(context, profileId) { LocalRecommendations.searched(it, serviceId, query, profileId) }
    }

    private fun flushObservations() {
        val batches = synchronized(pendingLock) {
            val result = pending.values.toList()
            pending.clear()
            flushScheduled = false
            result
        }
        batches.forEach { batch ->
            safeRun(batch.context, batch.profileId, batch.generation) {
                LocalRecommendations.observe(it, batch.items.values.toList())
            }
        }
    }

    private fun dispatch(context: Context, profileId: String, action: (Context) -> Unit) {
        val app = context.applicationContext
        if (IncognitoMode.isEnabled(app) || ProfileManager.getActiveProfileId(app) != profileId) return
        val generation = generation(app, profileId)
        worker.execute { safeRun(app, profileId, generation, action) }
    }

    private fun safeRun(context: Context, profileId: String, generation: Long, action: (Context) -> Unit) {
        try {
            synchronized(LocalRecommendations) {
                if (isCurrent(
                        profileId,
                        ProfileManager.getActiveProfileId(context),
                        generation,
                        generation(context, profileId),
                        IncognitoMode.isEnabled(context)
                    )
                ) {
                    action(context)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to update local recommendation model", error)
        }
    }

    private fun generation(context: Context, profileId: String): Long = context
        .getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
        .getLong("$profileId:generation", 0L)

    /** Test-only barrier for queued watch/like writes. Do not call from the main thread in the app. */
    internal fun awaitIdleForTesting(): Boolean {
        val latch = CountDownLatch(1)
        worker.execute { latch.countDown() }
        return latch.await(10, TimeUnit.SECONDS)
    }

    internal fun isCurrent(
        originalProfile: String,
        activeProfile: String,
        originalGeneration: Long,
        currentGeneration: Long,
        incognito: Boolean
    ): Boolean = originalProfile == activeProfile &&
        originalGeneration == currentGeneration && !incognito
}
