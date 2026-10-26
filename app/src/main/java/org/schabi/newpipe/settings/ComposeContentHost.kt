/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import coil3.SingletonImageLoader
import net.newpipe.app.platform.ContentHost
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

class ComposeContentHost(private val context: Context) : ContentHost {

    override fun applyImageQuality(quality: String) {
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.fromPreferenceKey(context, quality))
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    override fun applyYoutubeRestrictedMode(enabled: Boolean) = DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(enabled)

    override fun applyContentLocalization() {
        NewPipe.setupLocalization(
            Localization.getPreferredLocalization(context),
            Localization.getPreferredContentCountry(context)
        )
        PlayerHelper.resetFormat()
    }
}
