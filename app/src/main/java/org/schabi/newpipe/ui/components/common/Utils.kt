package org.schabi.newpipe.compose.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import androidx.window.core.layout.WindowWidthSizeClass
import org.schabi.newpipe.R
import org.schabi.newpipe.info_list.ItemViewMode

@Composable
fun determineItemViewMode(): ItemViewMode {
    val context = LocalContext.current
    val listMode = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(
            context.getString(R.string.list_view_mode_key),
            context.getString(R.string.list_view_mode_value)
        )

    return when (listMode) {
        context.getString(R.string.list_view_mode_list_key) -> ItemViewMode.LIST
        context.getString(R.string.list_view_mode_grid_key) -> ItemViewMode.GRID
        context.getString(R.string.list_view_mode_card_key) -> ItemViewMode.CARD
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
