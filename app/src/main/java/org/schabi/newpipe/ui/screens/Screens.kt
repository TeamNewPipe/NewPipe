/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Represents the screen keys for the app.
 */
sealed interface Screens : NavKey {
    sealed interface Settings : Screens {
        @Serializable
        data object Home : Settings

        @Serializable
        data object Player : Settings

        @Serializable
        data object Behaviour : Settings

        @Serializable
        data object Download : Settings

        @Serializable
        data object LookFeel : Settings

        @Serializable
        data object HistoryCache : Settings

        @Serializable
        data object Content : Settings

        @Serializable
        data object Feed : Settings

        @Serializable
        data object Services : Settings

        @Serializable
        data object Language : Settings

        @Serializable
        data object BackupRestore : Settings

        @Serializable
        data object Updates : Settings

        @Serializable
        data object Debug : Settings
    }
}
