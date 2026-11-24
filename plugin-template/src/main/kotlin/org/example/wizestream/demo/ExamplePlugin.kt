/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.example.wizestream.demo

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.plugin.WizeStreamPlugin
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginContext

/** Public no-argument entry class named by plugin.json. */
class ExamplePlugin : WizeStreamPlugin {
    override fun createService(serviceId: Int, context: WizeStreamPluginContext): StreamingService = ExampleService(serviceId, context)
}
