/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/** Keeps a player request cancellable until delivery and retries one concurrent cache refresh. */
internal fun <T> loadLocalMediaWithRefresh(
    executor: Executor,
    post: (Runnable) -> Unit,
    generation: () -> Long,
    read: (Long) -> T,
    onLoaded: (T) -> Unit
): Future<*> = LocalMediaPlayerLoad(executor, post, generation, read, onLoaded).also { it.start() }

private class LocalMediaPlayerLoad<T>(
    private val executor: Executor,
    private val post: (Runnable) -> Unit,
    private val generation: () -> Long,
    private val read: (Long) -> T,
    private val onLoaded: (T) -> Unit
) : FutureTask<Unit>({ Unit }) {
    private val lock = Any()
    private var worker: FutureTask<Unit>? = null

    fun start(attempt: Int = 0) {
        val task = FutureTask<Unit> {
            try {
                if (!isDone) {
                    val requestedGeneration = generation()
                    val result = read(requestedGeneration)
                    post(
                        Runnable {
                            synchronized(lock) {
                                if (isDone) return@Runnable
                                if (requestedGeneration != generation() && attempt == 0) {
                                    start(attempt + 1)
                                } else {
                                    // Busy providers must not starve the player: after one retry,
                                    // deliver the latest complete read. Cache writes still verify
                                    // their generation, and metadata reads verify file identity.
                                    try {
                                        onLoaded(result)
                                        super.run()
                                    } catch (error: Throwable) {
                                        setException(error)
                                    }
                                }
                            }
                        }
                    )
                }
            } catch (error: Throwable) {
                setException(error)
            }
            Unit
        }
        synchronized(lock) {
            if (isDone) return
            worker = task
        }
        try {
            executor.execute(task)
        } catch (error: Throwable) {
            setException(error)
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = synchronized(lock) {
        super.cancel(mayInterruptIfRunning).also { cancelled ->
            if (cancelled) worker?.cancel(mayInterruptIfRunning)
        }
    }
}
