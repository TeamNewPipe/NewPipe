/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

/**
 * Navigation display for compose screens
 * @param startDestination Starting destination for the activity/app
 */
@Composable
fun MainNavDisplay(startDestination: NavKey) {
    val backstack = rememberNavBackStack(Screen.config, startDestination)

    NavDisplay(
        backStack = backstack,
        entryProvider = entryProvider {

        }
    )
}
