/*
 * SPDX-FileCopyrightText: 2017-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.playqueue

import java.io.Serializable

sealed interface PlayQueueEvent : Serializable {
    fun type(): Type

    class InitEvent : PlayQueueEvent {
        override fun type() = Type.INIT
    }

    // sent when the index is changed
    class SelectEvent(val oldIndex: Int, val newIndex: Int) : PlayQueueEvent {
        override fun type() = Type.SELECT
    }

    // sent when more streams are added to the play queue
    class AppendEvent(val amount: Int) : PlayQueueEvent {
        override fun type() = Type.APPEND
    }

    // sent when a pending stream is removed from the play queue
    class RemoveEvent(val removeIndex: Int, val queueIndex: Int) : PlayQueueEvent {
        override fun type() = Type.REMOVE
    }

    // sent when two streams swap place in the play queue
    class MoveEvent(val fromIndex: Int, val toIndex: Int) : PlayQueueEvent {
        override fun type() = Type.MOVE
    }

    // sent when queue is shuffled
    class ReorderEvent(val fromSelectedIndex: Int, val toSelectedIndex: Int) : PlayQueueEvent {
        override fun type() = Type.REORDER
    }

    // sent when recovery record is set on a stream
    class RecoveryEvent(val index: Int, val position: Long) : PlayQueueEvent {
        override fun type() = Type.RECOVERY
    }

    // sent when the item at index has caused an exception
    class ErrorEvent(val errorIndex: Int, val queueIndex: Int) : PlayQueueEvent {
        override fun type() = Type.ERROR
    }

    // It is necessary only for use in java code. Remove it and use kotlin pattern
    // matching when all users of this enum are converted to kotlin
    enum class Type { INIT, SELECT, APPEND, REMOVE, MOVE, REORDER, RECOVERY, ERROR }
}
