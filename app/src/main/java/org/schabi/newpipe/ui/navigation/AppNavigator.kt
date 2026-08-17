package org.schabi.newpipe.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import org.schabi.newpipe.ui.navigation.AppDestination

class AppNavigator(
    private val startDestination: AppDestination = AppDestination.Home,
    private val onCloseRequest: () -> Unit = {}
) {
    val backstack = mutableStateListOf(startDestination)

    fun navigateTo(destination: AppDestination) {
        backstack.add(destination)
    }

    fun navigateToTab(destination: AppDestination) {
        backstack.clear()
        backstack.add(destination)
    }

    fun navigateUp() {
        if (backstack.size > 1) {
            backstack.removeAt(backstack.size - 1)
        } else {
            onCloseRequest()
        }
    }
}
