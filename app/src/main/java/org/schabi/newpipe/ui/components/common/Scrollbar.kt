package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun defaultThemedScrollbarSettings(): ScrollbarSettings = ScrollbarSettings.Default.copy(
    thumbUnselectedColor = MaterialTheme.colorScheme.primary,
    thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
)

@Composable
fun LazyColumnThemedScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    settings: ScrollbarSettings = defaultThemedScrollbarSettings(),
    indicatorContent: (@Composable (index: Int, isThumbSelected: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    LazyColumnScrollbar(
        state = state,
        modifier = modifier,
        settings = settings,
        indicatorContent = indicatorContent,
        content = content
    )
}
