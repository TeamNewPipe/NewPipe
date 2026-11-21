/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.error

import androidx.media3.datasource.HttpDataSource
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Report explicit HTTP access/rate-limit failures without exposing response URLs or headers.
 * Don't guess from generic numbers embedded in video IDs or unrelated parsing errors.
 */
internal object ServiceRestrictionMessages {
    enum class Kind {
        RATE_LIMIT,
        ACCESS_DENIED
    }

    private val httpCode = Regex(
        """\b(?:http(?:\s+status|\s+response\s+code)?|status|response\s+code)\s*[:=]?\s*(403|429)\b""",
        RegexOption.IGNORE_CASE
    )

    fun kind(throwable: Throwable?): Kind? {
        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current = throwable
        while (current != null && visited.add(current)) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                when (current.responseCode) {
                    429 -> return Kind.RATE_LIMIT
                    403 -> return Kind.ACCESS_DENIED
                }
            }
            val message = current.message.orEmpty().lowercase(Locale.ROOT)
            val code = httpCode.find(message)?.groupValues?.get(1)
            if (code == "429" || message.contains("too many requests")) {
                return Kind.RATE_LIMIT
            }
            if (code == "403") {
                return Kind.ACCESS_DENIED
            }
            current = current.cause
        }
        return null
    }
}
