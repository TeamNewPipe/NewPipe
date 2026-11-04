/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * Desktop has no debug tooling to drive yet
 */

@Singleton(binds = [DebugActions::class])
class JVMDebugActions : DebugActions by NoOpDebugActions
