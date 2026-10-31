/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

@Singleton(binds = [StringSetStore::class])
class IOSStringSetStore(settings: Settings, json: Json) : StringSetStore by JsonStringSetStore(settings, json)
