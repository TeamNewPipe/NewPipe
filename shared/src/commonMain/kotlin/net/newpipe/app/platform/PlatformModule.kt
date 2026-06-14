/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * Module to access various common implementations that are dependent upon platform.
 * See individual interfaces in this package and their implementations on platform packages
 * for the declarations included in this module.
 */
@Module
@ComponentScan
@Configuration
object PlatformModule
