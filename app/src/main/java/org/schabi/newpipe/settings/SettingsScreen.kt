package org.schabi.newpipe.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.TextPreference

@Composable
fun SettingsScreen(
    onSelectSettingOption: (SettingsScreenKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextPreference(
            title = R.string.settings_category_debug_title,
            onClick = { onSelectSettingOption(SettingsScreenKey.DEBUG) }
        )
        HorizontalDivider(color = Color.Black)
    }
}
