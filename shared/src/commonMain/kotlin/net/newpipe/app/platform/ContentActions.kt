/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger

interface ContentActions {
    val usesSystemAppLanguageSettings: Boolean

    fun systemAppLanguageName(): String?

    fun openSystemAppLanguageSettings()

    fun applyAppLanguage(languageCode: String)

    fun applyImageQuality(quality: String)

    fun applyYoutubeRestrictedMode(enabled: Boolean)

    fun applyContentLocalization()
}

internal object NoOpContentActions : ContentActions {

    override val usesSystemAppLanguageSettings = false

    override fun systemAppLanguageName(): String? = null

    override fun openSystemAppLanguageSettings() = logUnsupported("openSystemAppLanguageSettings")

    override fun applyAppLanguage(languageCode: String) = logUnsupported("applyAppLanguage")

    override fun applyImageQuality(quality: String) = logUnsupported("applyImageQuality")

    override fun applyYoutubeRestrictedMode(enabled: Boolean) = logUnsupported("applyYoutubeRestrictedMode")

    override fun applyContentLocalization() = logUnsupported("applyContentLocalization")

    private fun logUnsupported(action: String) = Logger.w(messageString = "Content action '$action' is unsupported on this platform")
}
