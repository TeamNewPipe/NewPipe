/*
 * SPDX-FileCopyrightText: 2022-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.ui.graphics.vector.ImageVector
import org.schabi.newpipe.R

enum class PlayerType(@StringRes val title: Int, val icon: ImageVector) {
    BACKGROUND(R.string.controls_background_title, Icons.Default.Headphones),
    MAIN(R.string.controls_main_title, Icons.AutoMirrored.Filled.PlaylistPlay),
    POPUP(R.string.controls_popup_title, Icons.Default.PictureInPicture)
}
