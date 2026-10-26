/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Hooks into the image, network and extractor helpers that still live in the current Android app
 */
interface ContentHost {

    fun applyImageQuality(quality: String)

    fun applyYoutubeRestrictedMode(enabled: Boolean)

    fun applyContentLocalization()
}
