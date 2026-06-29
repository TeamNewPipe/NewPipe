/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Intent
import org.koin.core.annotation.Singleton

/**
 * Placeholder [AndroidLegacyHooks] binding that satisfies Koin's compile-time
 * graph verification.
 *
 * At runtime the host activity (`NewPipeComposeActivity` in `:app`) registers
 * the real [AndroidLegacyHooks] implementation (`AppLegacyHooks`) through
 * `platformModules`. Koin's default override behavior means the host's
 * registration replaces this stub — so every method below should never be
 * called in a properly configured run.
 *
 * Delete this class once the legacy `:app` modules (`ErrorUtil`,
 * `NotificationWorker`, `HistoryRecordManager`, `NewVersionWorker`, etc.)
 * migrate into `:shared/androidMain` and a real implementation can live here.
 */
@Singleton(binds = [AndroidLegacyHooks::class])
internal class DefaultAndroidLegacyHooks : AndroidLegacyHooks {

    override fun showUiErrorSnackbar() = unsupported("showUiErrorSnackbar")
    override fun createErrorNotification() = unsupported("createErrorNotification")
    override fun runNotificationWorkerNow() = unsupported("runNotificationWorkerNow")
    override fun newLeakDisplayActivityIntent(): Intent? = null
    override fun requestDirectoryPicker(onPicked: (String) -> Unit) = unsupported("requestDirectoryPicker")
    override fun runManualUpdateCheck() = unsupported("runManualUpdateCheck")
    override fun wipeMetadataCache() = unsupported("wipeMetadataCache")
    override fun deleteWatchHistory() = unsupported("deleteWatchHistory")
    override fun deletePlaybackStates() = unsupported("deletePlaybackStates")
    override fun deleteSearchHistory() = unsupported("deleteSearchHistory")
    override fun clearRecaptchaCookies() = unsupported("clearRecaptchaCookies")
    override fun hasRecaptchaCookies(): Boolean = false
    override fun applyTheme(newThemeKey: String) = unsupported("applyTheme")
    override fun applyNightTheme(newNightThemeKey: String) = unsupported("applyNightTheme")
    override fun showSelectNightThemeToast() = unsupported("showSelectNightThemeToast")
    override fun openCaptionSettings() = unsupported("openCaptionSettings")
    override fun importDatabase() = unsupported("importDatabase")
    override fun exportDatabase() = unsupported("exportDatabase")
    override fun resetAllSettings() = unsupported("resetAllSettings")
    override fun importSubscriptions() = unsupported("importSubscriptions")
    override fun exportSubscriptions() = unsupported("exportSubscriptions")
    override fun openMainPageTabsChooser() = unsupported("openMainPageTabsChooser")
    override fun openPeertubeInstanceList() = unsupported("openPeertubeInstanceList")
    override fun onAppLanguageChanged() = unsupported("onAppLanguageChanged")

    private fun unsupported(method: String): Nothing = error(
        "AndroidLegacyHooks.$method called without a host binding — " +
                "NewPipeComposeActivity.platformModules() must register AppLegacyHooks."
    )
}
