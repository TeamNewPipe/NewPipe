/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.schabi.newpipe.plugin.PluginManager

/** Keeps a framework-supplied loader while resolving plugin-owned saved-state classes. */
object PluginStateClassLoader {
    @JvmStatic
    fun withPlugins(parent: ClassLoader?): ClassLoader {
        val plugins = PluginManager.hostAndPluginsClassLoader
        if (parent == null || parent === plugins) return plugins
        return object : ClassLoader(parent) {
            override fun findClass(name: String): Class<*> = Class.forName(name, false, plugins)
        }
    }
}
