/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Contract adapted from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 */
package org.schabi.newpipe.extractor.exceptions

open class WebViewUnavailableException : ExtractionException {
    private val retryable: Boolean

    constructor(message: String) : this(message, false, null)

    constructor(message: String, cause: Throwable?) : this(message, true, cause)

    constructor(message: String, retryable: Boolean, cause: Throwable?) : super(message, cause) {
        this.retryable = retryable
    }

    fun isRetryable(): Boolean = retryable
}
