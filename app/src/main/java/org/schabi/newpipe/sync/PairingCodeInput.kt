/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

/** Only remove clipboard whitespace; signed contents are verified by the usual pairing path. */
internal object PairingCodeInput {
    fun normalize(value: CharSequence): String {
        val code = value.trim()
        if (code.isEmpty()) {
            throw PairingException("The pairing code is empty")
        }
        if (code.length > PairingSecurity.MAX_PAIRING_URI_LENGTH) {
            throw PairingException("The pairing code is too large")
        }
        return code.toString()
    }
}
