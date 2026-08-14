/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings.model

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class SettingsCategory(
    val title: StringResource,
    val icon: DrawableResource,
    // TODO: Replace with a Destination once sub-screens are migrated
    val onClick: () -> Unit = {}
)
