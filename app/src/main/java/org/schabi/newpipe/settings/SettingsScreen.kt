package org.schabi.newpipe.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.SettingsRoutes
import org.schabi.newpipe.ui.TextPreference
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingExtraSmall

@Composable
fun SettingsScreen(
    onSelectSettingOption: (settingsRoute: SettingsRoutes) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextPreference(
            title = R.string.settings_category_debug_title,
            onClick = { onSelectSettingOption(SettingsRoutes.SettingsDebugRoute) }
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground,
            thickness = 0.6.dp,
            modifier = Modifier.padding(horizontal = SpacingExtraSmall)
        )
    }
}
