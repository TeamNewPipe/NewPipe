package org.schabi.newpipe.ui

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import org.schabi.newpipe.R

// Settings screens
@Serializable
sealed class SettingsRoutes(
    @get:StringRes
    val screenTitleRes: Int
) {
    @Serializable
    object SettingsMainRoute : SettingsRoutes(R.string.settings)
    @Serializable
    object SettingsDebugRoute : SettingsRoutes(R.string.settings_category_debug_title)
}
