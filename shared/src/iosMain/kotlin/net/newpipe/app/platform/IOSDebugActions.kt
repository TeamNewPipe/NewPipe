/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * iOS has no debug tooling to drive yet, and the Debug category is hidden there anyway
 */
@Singleton(binds = [DebugActions::class])
class IOSDebugActions : DebugActions by NoOpDebugActions
