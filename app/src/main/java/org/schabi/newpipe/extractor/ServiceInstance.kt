/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Contract adapted from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 */
package org.schabi.newpipe.extractor

/** One service endpoint, identified by its URL independently of its display name. */
class ServiceInstance(val name: String, val url: String) {
    override fun equals(other: Any?): Boolean = other is ServiceInstance && url == other.url

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "$name ($url)"
}
