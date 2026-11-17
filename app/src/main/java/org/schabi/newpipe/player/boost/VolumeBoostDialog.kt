/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.boost

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player

object VolumeBoostDialog {
    @JvmStatic
    fun show(context: Context, player: Player) {
        val gains = VolumeBoostController.supportedGainsMb
        val labels = gains.map { gainMb ->
            if (gainMb == 0) {
                context.getString(R.string.off)
            } else {
                context.getString(R.string.volume_boost_level, gainMb / 100)
            }
        }.toTypedArray()
        val selected = gains.indexOf(player.volumeBoostGainMb).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.volume_boost)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                player.volumeBoostGainMb = gains[index]
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
