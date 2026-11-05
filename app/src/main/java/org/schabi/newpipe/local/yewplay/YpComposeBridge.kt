/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import io.github.yewplay.core.designsystem.YpMosaicCover
import io.github.yewplay.core.designsystem.YpTextTabs
import io.github.yewplay.core.designsystem.YpTheme
import java.util.function.IntConsumer

/** YewPlay: lets the Java screens show the Compose components of the design system. */
object YpComposeBridge {
    /** Shows YewPlay text tabs in [view]; [onSelect] receives the index of the tapped tab. */
    @JvmStatic
    fun setTextTabs(view: ComposeView, labels: List<String>, selectedIndex: Int, onSelect: IntConsumer) {
        val context = view.context
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            YpTheme(YpThemePrefs.themeKey(context), YpThemePrefs.nightThemeKey(context)) {
                YpTextTabs(
                    labels = labels,
                    selectedIndex = selectedIndex,
                    onSelect = { onSelect.accept(it) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }

    /** Shows a playlist cover mosaic of the given thumbnails in [view]. */
    @JvmStatic
    fun setMosaicCover(view: ComposeView, thumbnailUrls: List<String?>) {
        val context = view.context
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            YpTheme(YpThemePrefs.themeKey(context), YpThemePrefs.nightThemeKey(context)) {
                YpMosaicCover(thumbnailUrls, Modifier.fillMaxSize())
            }
        }
    }
}
