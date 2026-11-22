/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableEmitter
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.exceptions.Exceptions
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Collections
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity

/** Serializes this fragment's pending saves before handing its saved order to selection. */
class PlaylistSelectionSaveGate : AutoCloseable {
    class SavedOrder internal constructor(val streamIds: List<Long>, internal val sequence: Long)

    private val lock = Any()
    private val pending = mutableSetOf<Long>()
    private val idleWaiters = mutableSetOf<CompletableEmitter>()
    private var sequence = 0L
    private var lastFinished = -1L
    private var lastSucceeded = false
    private var worker: Scheduler.Worker? = null
    private var closed = false

    val hasPendingWrites: Boolean
        get() = synchronized(lock) { pending.isNotEmpty() }

    fun submit(streamIds: List<Long>, write: () -> Unit): Single<SavedOrder> {
        val ids = Collections.unmodifiableList(ArrayList(streamIds))
        return Single.create { emitter ->
            synchronized(lock) {
                if (closed) {
                    emitter.onError(IllegalStateException("Playlist save gate is closed"))
                    return@create
                }
                val saved = SavedOrder(ids, ++sequence)
                pending.add(saved.sequence)
                val writer = worker ?: Schedulers.io().createWorker().also { worker = it }
                // A subscribed save must finish even if its UI observer is disposed meanwhile.
                writer.schedule {
                    try {
                        write()
                        finished(saved, true)
                        if (!emitter.isDisposed) emitter.onSuccess(saved)
                        signalIdle()
                    } catch (error: Throwable) {
                        finished(saved, false)
                        Exceptions.throwIfFatal(error)
                        if (!emitter.isDisposed) emitter.onError(error)
                        signalIdle()
                    }
                }
            }
        }
    }

    fun write(database: AppDatabase, profileId: String, playlistId: Long, streamIds: List<Long>): Single<SavedOrder> {
        val ids = streamIds.toList()
        return submit(ids) {
            database.runInTransaction {
                val streams = database.playlistStreamDAO()
                check(streams.playlistBelongsToProfile(profileId, playlistId)) { "Playlist no longer belongs to this profile" }
                streams.deleteBatchForProfile(profileId, playlistId)
                streams.insertAllForProfile(profileId, playlistId, ids.mapIndexed { index, id -> PlaylistStreamEntity(playlistId, id, index) })
            }
        }
    }

    fun canClearDirty(saved: SavedOrder, currentStreamIds: List<Long>): Boolean = synchronized(lock) {
        !closed && pending.isEmpty() && lastSucceeded && lastFinished == saved.sequence && saved.streamIds == currentStreamIds
    }

    /** Survives an earlier view's disposed callback while retaining no disposed waiter. */
    fun awaitIdle(): Completable = Completable.create { emitter ->
        synchronized(lock) {
            if (pending.isEmpty()) emitter.onComplete() else idleWaiters.add(emitter)
        }
        emitter.setCancellable { synchronized(lock) { idleWaiters.remove(emitter) } }
    }

    private fun signalIdle() = synchronized(lock) {
        if (pending.isEmpty()) {
            val waiting = idleWaiters.toList()
            idleWaiters.clear()
            waiting.forEach { if (!it.isDisposed) it.onComplete() }
        }
    }

    private fun finished(saved: SavedOrder, succeeded: Boolean) = synchronized(lock) {
        pending.remove(saved.sequence)
        lastFinished = saved.sequence
        lastSucceeded = succeeded
        if (pending.isEmpty()) {
            worker?.dispose()
            worker = null
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        if (pending.isEmpty()) {
            worker?.dispose()
            worker = null
        }
    }
}
