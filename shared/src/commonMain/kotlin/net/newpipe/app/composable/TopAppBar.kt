/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.BuildConfig
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.currentServiceTopAppBarColors
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_arrow_back
import newpipe.shared.generated.resources.navigate_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A top app bar composable to be used with Scaffold
 * @param modifier The modifier to be applied to the composable
 * @param title Title of the screen
 * @param navigationIcon Icon for the navigation button
 * @param onNavigateUp Action when user clicks the navigation icon
 * @param actions Actions to display on the top app bar (for e.g. menu)
 */
@Composable
fun TopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigationIcon: Painter = painterResource(Res.drawable.ic_arrow_back),
    onNavigateUp: (() -> Unit)? = null,
    colors: TopAppBarColors = currentServiceTopAppBarColors(),
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    TopAppBar(
        modifier = modifier,
        colors = colors,
        title = { if (title != null) Text(text = title) },
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        painter = navigationIcon,
                        contentDescription = stringResource(Res.string.navigate_back)
                    )
                }
            }
        },
        actions = actions
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TopAppBarPreview() {
    TopAppBar(
        title = BuildConfig.APP_NAME,
        onNavigateUp = {}
    )
}
