/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * Settings module to access key-value pairs across different platforms.
 * See individual platform packages for the declarations included in this module.
 */
@Module
@ComponentScan
@Configuration
class SettingsModule
