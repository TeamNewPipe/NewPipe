/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import android.content.Intent

/**
 * Temporary bridge for Android-only operations that still live in the legacy `:app`
 * module (ErrorUtil, NotificationWorker, the LeakCanary reflection bootstrap).
 *
 * Delete this file once those modules are migrated to `:shared/androidMain` and
   inline their calls into [AndroidDebugActions].
 * */
interface AndroidLegacyHooks {
    // DebugActions / ErrorUtil / LeakCanary
    fun showUiErrorSnackbar()
    fun createErrorNotification()
    fun runNotificationWorkerNow()
    fun newLeakDisplayActivityIntent(): Intent?

    // DownloadActions
    fun requestDirectoryPicker(onPicked: (uri: String) -> Unit)

    // UpdateActions
    fun runManualUpdateCheck()

    // HistoryActions
    fun wipeMetadataCache()
    fun deleteWatchHistory()
    fun deletePlaybackStates()
    fun deleteSearchHistory()
    fun clearRecaptchaCookies()
    fun hasRecaptchaCookies(): Boolean

    // LookFeelActions
    fun applyTheme(newThemeKey: String)
    fun applyNightTheme(newNightThemeKey: String)
    fun showSelectNightThemeToast()
    fun openCaptionSettings()

    // BackupRestoreActions
    fun importDatabase()
    fun exportDatabase()
    fun resetAllSettings()
    fun importSubscriptions()
    fun exportSubscriptions()

    // ContentActions
    fun openMainPageTabsChooser()
    fun openPeertubeInstanceList()
    fun onAppLanguageChanged()
}