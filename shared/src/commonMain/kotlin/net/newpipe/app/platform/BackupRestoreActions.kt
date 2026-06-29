/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform side-effects triggered from the Backup & Restore settings screen.
 *
 * Android: each action opens the appropriate SAF picker via launchers
 * registered by the host activity, then runs the corresponding flow
 * (DB checkpoint, ZIP I/O, optional settings import, app restart) via the
 * legacy [ImportExportManager] in `:app`.
 *
 */
interface BackupRestoreActions {
    /** Open file picker for a NewPipe ZIP export and import it (DB + optional settings). */
    fun importDatabase()

    /** Open save-as picker and export DB + settings into a NewPipe ZIP. */
    fun exportDatabase()

    /** Clear all SharedPreferences after the screen's confirm dialog accepts, then restart. */
    fun resetAllSettings()

    /** Open file picker for a subscriptions .json and import them. */
    fun importSubscriptions()

    /** Open save-as picker and export subscriptions to .json. */
    fun exportSubscriptions()
}