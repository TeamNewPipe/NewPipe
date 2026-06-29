/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.content.Context
import android.os.Bundle
import net.newpipe.app.ComposeActivity
import net.newpipe.app.platform.AndroidLegacyHooks
import org.koin.core.module.Module
import org.koin.dsl.module
import org.schabi.newpipe.platform.AppLegacyHooks
import org.schabi.newpipe.platform.DirectoryPickerRegistry
import org.schabi.newpipe.platform.ImportExportLauncherRegistry

/**
 * `:app`-side host that extends the shared [ComposeActivity] and provides
 * the legacy bridge bindings (Context, AndroidLegacyHooks → AppLegacyHooks).
 *
 * Required while parts of NewPipe still live as Views in `:app`. Once the
 * migration completes this class is deleted and the manifest entry points
 * directly at [net.newpipe.app.ComposeActivity].
 */
class NewPipeComposeActivity : ComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DirectoryPickerRegistry.bindTo(this)
        ImportExportLauncherRegistry.bindTo(this)
        super.onCreate(savedInstanceState)
    }

    override fun platformModules(): List<Module> = listOf(
        module {
            single<Context> { applicationContext }
            single<AndroidLegacyHooks> { AppLegacyHooks(application) }
        }
    )
}
