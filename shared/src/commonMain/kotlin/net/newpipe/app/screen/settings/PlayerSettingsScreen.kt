/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.PlayerSettingsSections
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.settings_category_exoplayer_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PlayerSettingsScreen(
    navigator: Navigator = koinInject(),
    sections: PlayerSettingsSections = koinInject()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_exoplayer_title),
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            sections.Render()
        }
    }
}
