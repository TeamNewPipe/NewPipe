/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

/**
 * Native host metadata. The profile identity and preferences belong to the profile captured when
 * the service was created; a later profile switch must not redirect old work to another profile.
 */
interface WizeStreamPluginContext : PluginContext {
    fun getHostVersionCode(): Int

    fun getHostVersionName(): String

    fun getProfileId(): String
}
