/*
 * SPDX-FileCopyrightText: 2017-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {

    @Serializable
    data object About : Screen

    @Serializable
    data object Error : Screen

    sealed interface Settings : Screen {
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
