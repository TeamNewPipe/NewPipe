package org.schabi.newpipe.learning

import java.util.Locale

object LearningNoteTime {
    @JvmStatic
    fun format(timestampMillis: Long): String {
        val totalSeconds = timestampMillis.coerceAtLeast(0) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    @JvmStatic
    fun parse(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 2..3 || parts.any { it.isBlank() || it.any { c -> !c.isDigit() } }) {
            return null
        }
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        val hours = if (numbers.size == 3) numbers[0] else 0
        val minutes = numbers[numbers.size - 2]
        val seconds = numbers.last()
        if (minutes !in 0..59 || seconds !in 0..59) {
            return null
        }
        return runCatching { ((hours * 60 + minutes) * 60 + seconds) * 1_000 }.getOrNull()
    }
}
