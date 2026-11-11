/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.model

import net.newpipe.app.model.SettingsFormat

data class SettingsImport(val file: String, val format: SettingsFormat)
