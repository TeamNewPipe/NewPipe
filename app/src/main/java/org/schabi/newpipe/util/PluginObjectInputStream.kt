/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.plugin.PluginRuntimeLoader

/** Restores app-created extractor snapshots whose concrete classes may belong to a plugin. */
class PluginObjectInputStream(input: InputStream) : ObjectInputStream(input) {
    override fun resolveClass(descriptor: ObjectStreamClass): Class<*> {
        return try {
            super.resolveClass(descriptor)
        } catch (missing: ClassNotFoundException) {
            PluginRuntimeLoader.findRuntimeClass(descriptor.name)
                ?: PluginManager.findPluginClass(descriptor.name)
                ?: throw missing
        }
    }
}
