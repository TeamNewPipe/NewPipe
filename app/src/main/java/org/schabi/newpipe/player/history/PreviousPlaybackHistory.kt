/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.history

import android.content.Context
import android.content.Intent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.recovery.QueueRecovery
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SerializedCache

/** An explicit navigation action restores this queue, including its pending resume position. */
class PreviousPlayback internal constructor(
    val queue: PlayQueue,
    val playWhenReady: Boolean,
    val repeatMode: Int
) {
    private var dispatchedQueue: PlayQueue? = null

    fun playerIntent(context: Context): Intent? {
        val intent = NavigationHelper.getPlayerIntent(
            context,
            PlayerService::class.java,
            queue,
            PlayerIntentType.AllOthers
        ).putExtra(Player.PLAY_WHEN_READY, playWhenReady)
            .putExtra(Player.RESUME_PLAYBACK, false)
            // Even an equal queue must restore the saved order/index/position, not reuse a live one.
            .putExtra(QueueRecovery.RESTORE_EXTRA, true)
        val key = intent.getStringExtra(Player.PLAY_QUEUE_KEY) ?: return null
        // PlayerIntentController takes this same instance. An equal, still-live old queue must
        // not acknowledge the restore before the replacement/restore intents have been handled.
        dispatchedQueue = SerializedCache.getInstance().get(key, PlayQueue::class.java) ?: return null
        return intent
    }

    internal fun accepts(value: PlayQueue): Boolean = value === dispatchedQueue
}

/**
 * Small, memory-only navigation history. Serialization keeps the concrete queue's pagination,
 * shuffle backup, duplicate occurrences and source metadata without sharing mutable queue items.
 * Only bytes produced here are deserialized; this is not an external data format.
 */
internal class PreviousPlaybackHistory {
    private class Entry(
        val bytes: ByteArray,
        val position: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int
    )

    private val entries = ArrayDeque<Entry>()
    private var bytesRetained = 0
    val isEmpty: Boolean get() = entries.isEmpty()

    fun push(queue: PlayQueue, position: Long, playWhenReady: Boolean, repeatMode: Int): Boolean {
        val bytes = if (queue.size() in 1..MAX_ITEMS && queue.index in 0 until queue.size()) {
            try {
                val output = LimitedOutput()
                ObjectOutputStream(output).use { it.writeObject(queue) }
                output.bytes()
            } catch (_: IOException) {
                null
            }
        } else {
            null
        }
        if (bytes == null) {
            // Do not label an older entry as the immediately previous playback.
            clear()
            return false
        }
        while (entries.size >= MAX_ENTRIES || bytesRetained + bytes.size > MAX_TOTAL_BYTES) {
            discardFirst()
        }
        entries.addLast(Entry(bytes, position.coerceAtLeast(0), playWhenReady, repeatMode))
        bytesRetained += bytes.size
        return true
    }

    fun pop(): PreviousPlayback? {
        val entry = entries.removeLastOrNull() ?: return null
        bytesRetained -= entry.bytes.size
        return try {
            val queue = ObjectInputStream(ByteArrayInputStream(entry.bytes)).use {
                it.readObject() as PlayQueue
            }
            queue.setRecovery(queue.index, entry.position)
            PreviousPlayback(queue, entry.playWhenReady, entry.repeatMode)
        } catch (_: IOException) {
            clear()
            null
        } catch (_: ClassNotFoundException) {
            clear()
            null
        } finally {
            entry.bytes.fill(0)
        }
    }

    fun clear() {
        while (entries.isNotEmpty()) discardFirst()
    }

    private fun discardFirst() {
        val discarded = entries.removeFirst()
        bytesRetained -= discarded.bytes.size
        discarded.bytes.fill(0)
    }

    private class LimitedOutput : OutputStream() {
        private val output = ByteArrayOutputStream()

        override fun write(value: Int) {
            checkSize(1)
            output.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkSize(length)
            output.write(bytes, offset, length)
        }

        private fun checkSize(length: Int) {
            if (length > MAX_ENTRY_BYTES - output.size()) throw IOException("Queue snapshot is too large")
        }

        fun bytes(): ByteArray = output.toByteArray()
    }

    companion object {
        internal const val MAX_ENTRIES = 3
        internal const val MAX_ITEMS = 1_000
        internal const val MAX_ENTRY_BYTES = 1_048_576
        private const val MAX_TOTAL_BYTES = 2 * MAX_ENTRY_BYTES
    }
}
