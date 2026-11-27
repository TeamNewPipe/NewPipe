/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.model

/**
 * One-off feedback for the user after trying to add a PeerTube instance
 *
 * Set by [net.newpipe.app.viewmodel.settings.PeertubeInstanceSettingsViewModel.add] and shown
 * as a snackbar by the PeerTube instance settings screen, which then clears it again
 */

enum class PeertubeInstanceMessage {
    /** The entered URL isn't an https URL */
    HTTPS_ONLY,

    /** An instance with the same URL is already in the list */
    ALREADY_EXISTS,

    /** The instance couldn't be reached or isn't a PeerTube instance */
    ADD_FAILED
}
