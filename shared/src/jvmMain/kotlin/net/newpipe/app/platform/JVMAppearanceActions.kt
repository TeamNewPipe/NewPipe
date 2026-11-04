/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton
import java.io.IOException

@Singleton(binds = [AppearanceActions::class])
class JVMAppearanceActions : AppearanceActions {

    private val captionSettingsCommand: List<String>? = when (currentOs()) {
        DesktopOs.WINDOWS ->
            // Closed-caption styling page
            listOf("cmd", "/c", "start", "", "ms-settings:easeofaccess-closedcaptioning")

        // Subtitles & captions styling pane. This is community-documented,so on newer macOS it may open the Accessibility pane without directing to Captions.
        DesktopOs.MAC ->
            listOf("open", "x-apple.systempreferences:com.apple.preference.universalaccess?Captions")

        // Nothing for Linux yet
        DesktopOs.LINUX,
        DesktopOs.UNKNOWN -> null
    }

    override fun isCaptionSettingsAvailable() = captionSettingsCommand != null

    override fun openCaptionSettings() {
        val command = captionSettingsCommand ?: return
        try {
            ProcessBuilder(command).start()
        } catch (exception: IOException) {
            Logger.e(messageString = "Could not open caption settings", throwable = exception)
        }
    }

    override fun applyThemeChange(theme: String) = Unit

    private enum class DesktopOs { WINDOWS, MAC, LINUX, UNKNOWN }

    private fun currentOs(): DesktopOs {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            osName.startsWith("windows") -> DesktopOs.WINDOWS
            osName.startsWith("mac") -> DesktopOs.MAC
            osName.contains("nux") || osName.contains("nix") || osName.contains("aix") -> DesktopOs.LINUX
            else -> DesktopOs.UNKNOWN
        }
    }
}