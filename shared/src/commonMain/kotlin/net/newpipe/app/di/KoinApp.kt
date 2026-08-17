/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di

import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.ComponentScan

/**
 * Entry point for Koin-related configuration
 */
@KoinApplication
@ComponentScan("net.newpipe.app")
object KoinApp
