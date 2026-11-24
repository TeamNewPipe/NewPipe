/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Contract adapted from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 */
package org.schabi.newpipe.extractor

import org.schabi.newpipe.extractor.exceptions.ExtractionException

/** Optional instance management implemented and persisted by an individual service. */
interface ServiceInstances {
    /** Saved endpoints in display order, including the current or default endpoint. */
    fun getInstances(): List<ServiceInstance>

    fun getCurrentInstance(): ServiceInstance

    /** Validates a user-entered URL; hosts must call this potentially networked method off-thread. */
    @Throws(ExtractionException::class)
    fun addInstance(url: String): ServiceInstance

    fun selectInstance(instance: ServiceInstance)

    /** Retains the current endpoint; an empty list resets the service to its default. */
    fun setInstances(instances: List<ServiceInstance>)

    fun getInstancesHelpUrl(): String?
}
