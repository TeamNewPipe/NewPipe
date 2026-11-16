/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.days
import newpipe.shared.generated.resources.hours
import newpipe.shared.generated.resources.minutes
import newpipe.shared.generated.resources.seconds
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Names a duration by its largest whole unit, e.g. "4 hours", as the old DurationListPreference did
 */
@Composable
fun localizedDuration(duration: Duration): String = when {
    duration >= 1.days -> {
        val days = duration.inWholeDays.toInt()
        pluralStringResource(Res.plurals.days, days, days)
    }

    duration >= 1.hours -> {
        val hours = duration.inWholeHours.toInt()
        pluralStringResource(Res.plurals.hours, hours, hours)
    }

    duration >= 1.minutes -> {
        val minutes = duration.inWholeMinutes.toInt()
        pluralStringResource(Res.plurals.minutes, minutes, minutes)
    }

    else -> {
        val seconds = duration.inWholeSeconds.toInt()
        pluralStringResource(Res.plurals.seconds, seconds, seconds)
    }
}
