package org.schabi.newpipe.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.viewmodel.SettingsViewModel
import org.schabi.newpipe.ui.SwitchPreference
import org.schabi.newpipe.ui.theme.SizeTokens

@Composable
fun DebugScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val settingsLayoutRedesign by viewModel.settingsLayoutRedesign.collectAsState()

    Column(modifier = modifier) {
        SwitchPreference(
            modifier = Modifier.padding(SizeTokens.SpacingExtraSmall),
            R.string.settings_layout_redesign,
            settingsLayoutRedesign,
            viewModel::toggleSettingsLayoutRedesign
        )
    }
}
