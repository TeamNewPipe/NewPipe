package org.schabi.newpipe.ui.components.common

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import androidx.window.core.layout.WindowWidthSizeClass
import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.ItemViewMode

@Composable
fun determineItemViewMode(): ItemViewMode {
    val listMode = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        .getString(
            stringResource(R.string.list_view_mode_key),
            stringResource(R.string.list_view_mode_value)
        )

    return when (listMode) {
        stringResource(R.string.list_view_mode_list_key) -> ItemViewMode.LIST
        stringResource(R.string.list_view_mode_grid_key) -> ItemViewMode.GRID
        stringResource(R.string.list_view_mode_card_key) -> ItemViewMode.CARD
        else -> {
            // Auto mode - evaluate whether to use Grid based on screen real estate.
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
                ItemViewMode.GRID
            } else {
                ItemViewMode.LIST
            }
        }
    }
}
