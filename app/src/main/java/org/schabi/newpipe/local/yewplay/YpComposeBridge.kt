/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.yewplay.core.designsystem.YpMosaicCover
import io.github.yewplay.core.designsystem.YpTheme

/** YewPlay: lets the Java screens show the Compose components of the design system. */
object YpComposeBridge {
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
