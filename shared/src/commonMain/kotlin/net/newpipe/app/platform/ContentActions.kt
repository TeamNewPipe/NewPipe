/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

/**
 * Platform-specific actions triggered from the Content settings screen.
 */
interface ContentActions {
    /**
     * Opens the system app-locale settings (Android 13+), where the user can pick
     * the app language.
     */
    fun openAppLanguageSettings()

    /**
     * Clears the in-memory and on-disk image caches after the image quality changed.
     */
    fun wipeImageCaches()

    /**
     * Updates the YouTube restricted mode cookies after the toggle changed.
     */
    fun updateYoutubeRestrictedModeCookies()

    /**
     * Re-applies the preferred localization and content country after
     * language-related settings changed.
     */
    fun reloadLocalization()

    /**
     * Returns the display name of the currently selected app language, or null
     * when the system language is used (or the platform cannot determine it).
     */
    fun appLanguageLabel(): String?
}