/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Contract adapted from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 */
package org.schabi.newpipe.extractor.exceptions

/** An automated-traffic block the service says can be resolved by logging in. */
open class AntiBotLoginRequiredException : AntiBotException {
    constructor(serviceId: Int, message: String) : super(serviceId, message)

    constructor(serviceId: Int, message: String, cause: Throwable?) : super(serviceId, message, cause)
}
