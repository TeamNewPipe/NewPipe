/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Helper for working with platform-specific resources
 * See individual platform classes for real implementation.
 */
interface ResourceHandler {

    /**
     * Reads the resource at the given path
     */
    fun readResourceToString(path: String): String
}
