package org.schabi.newpipe.ui.components.items

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
    val prefValue = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        .getString(stringResource(R.string.list_view_mode_key), null)
    val viewMode = prefValue?.let { ItemViewMode.valueOf(it.uppercase()) } ?: ItemViewMode.AUTO

    return when (viewMode) {
        ItemViewMode.AUTO -> {
            // Evaluate whether to use Grid based on screen real estate.
            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
                ItemViewMode.GRID
            } else {
                ItemViewMode.LIST
            }
        }
        else -> viewMode
    }
}
