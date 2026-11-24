/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Contract adapted from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 */
package org.schabi.newpipe.extractor

import org.schabi.newpipe.extractor.exceptions.WebViewUnavailableException

fun interface WebViewAvailabilityChecker {
    @Throws(WebViewUnavailableException::class)
    fun checkWebViewAvailable()
}
