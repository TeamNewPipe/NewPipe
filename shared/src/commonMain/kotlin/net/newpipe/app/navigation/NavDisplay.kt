/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf

/**
 * Navigation display for compose screens
 * @param startDestination Starting destination for the app
 * @param navigator Navigator to help with navigation, injected by Koin
 * @param onCloseRequest Callback to close the app
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun NavDisplay(
    startDestination: Destination,
    onCloseRequest: () -> Unit,
    navigator: Navigator = koinInject {
        parametersOf(startDestination, onCloseRequest)
    }
) {
    val backstack = rememberSerializable(serializer = SnapshotStateListSerializer<Destination>()) {
        navigator.backstack
    }

    NavDisplay(
        backStack = backstack,
        onBack = { navigator.navigateUp() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = koinEntryProvider()
    )
}
