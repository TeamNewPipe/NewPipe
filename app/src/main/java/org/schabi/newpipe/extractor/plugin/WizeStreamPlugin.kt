/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import org.schabi.newpipe.extractor.StreamingService

/** Entry contract for packages declaring host=wizestream and apiVersion=2.0. */
interface WizeStreamPlugin {
    fun createService(serviceId: Int, context: WizeStreamPluginContext): StreamingService
}
