/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.local.playlist

sealed interface PlaylistTransferDestination {
    data class Existing(val playlistId: Long) : PlaylistTransferDestination
    data class Create(val name: String) : PlaylistTransferDestination
}
